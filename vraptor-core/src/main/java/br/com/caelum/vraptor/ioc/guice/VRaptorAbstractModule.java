package br.com.caelum.vraptor.ioc.guice;

import static com.google.inject.matcher.Matchers.annotatedWith;
import static com.google.inject.matcher.Matchers.not;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.caelum.vraptor.ComponentRegistry;
import br.com.caelum.vraptor.core.BaseComponents;
import br.com.caelum.vraptor.core.RequestInfo;
import br.com.caelum.vraptor.http.MutableRequest;
import br.com.caelum.vraptor.http.MutableResponse;
import br.com.caelum.vraptor.ioc.ApplicationScoped;
import br.com.caelum.vraptor.ioc.Container;
import br.com.caelum.vraptor.ioc.RequestScoped;
import br.com.caelum.vraptor.ioc.SessionScoped;
import br.com.caelum.vraptor.ioc.StereotypeHandler;
import br.com.caelum.vraptor.ioc.spring.VRaptorRequestHolder;
import br.com.caelum.vraptor.serialization.HTMLSerialization;
import br.com.caelum.vraptor.serialization.Serialization;
import br.com.caelum.vraptor.validator.BeanValidator;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.matcher.Matcher;
import com.google.inject.multibindings.Multibinder;

/**
 *
 * An AbstractModule that wires VRaptor components.
 *
 * @author Lucas Cavalcanti
 * @author Sergio Lopes
 *
 * @since 3.2
 *
 */
public class VRaptorAbstractModule extends AbstractModule {

	static final class SetToListProvider<T> implements Provider<List<T>> {
		private final Set<T> set;
		@Inject
		public SetToListProvider(Set<T> set) {
			this.set = set;
		}
		public List<T> get() {
			return new ArrayList<T>(set);
		}
	}

	private static final Logger logger = LoggerFactory.getLogger(VRaptorAbstractModule.class);

	private final ServletContext context;
	private final Container container;

	public VRaptorAbstractModule(ServletContext context, Container container) {
		this.context = context;
		this.container = container;
	}

	@Override
	protected void configure() {
		bindScope(RequestScoped.class, GuiceProvider.REQUEST);
		bindScope(SessionScoped.class, GuiceProvider.SESSION);
		bindScope(ApplicationScoped.class, GuiceProvider.APPLICATION);

		Matcher<TypeLiteral<?>> isApplication = type(annotatedWith(ApplicationScoped.class));
		Matcher<TypeLiteral<?>> isSession = type(annotatedWith(SessionScoped.class));

		bindListener(isApplication, new ScopeLifecycleListener(GuiceProvider.APPLICATION));
		bindListener(isSession, new ScopeLifecycleListener(GuiceProvider.SESSION));
		bindListener(not(isApplication).and(not(isSession)), new ScopeLifecycleListener(GuiceProvider.REQUEST));

		requestInfoBindings();

		bind(Container.class).toInstance(container);


		GuiceComponentRegistry registry = new GuiceComponentRegistry(binder());

		bind(ComponentRegistry.class).toInstance(registry);

		registry.registerInScope((Map) BaseComponents.getApplicationScoped(), GuiceProvider.APPLICATION);
		registry.registerInScope((Map) BaseComponents.getPrototypeScoped(), Scopes.NO_SCOPE);
		registry.registerInScope((Map) BaseComponents.getRequestScoped(), GuiceProvider.REQUEST);

		for (Class converter : BaseComponents.getBundledConverters()) {
			registry.register(converter, converter);
		}
		for (Class handler : BaseComponents.getStereotypeHandlers()) {
			registry.register(handler, handler);
			Multibinder<StereotypeHandler> stereotypeHandlers = Multibinder.newSetBinder(binder(), StereotypeHandler.class);
			stereotypeHandlers.addBinding().to(handler);
		}

		for (Entry<Class<?>, Class<?>> entry : BaseComponents.getCachedComponents().entrySet()) {
			registry.register(entry.getKey(), entry.getValue());
		}

		final Multibinder<Serialization> serializationBinder = Multibinder.newSetBinder(binder(), Serialization.class);
		serializationBinder.addBinding().to(HTMLSerialization.class);

		TypeLiteral<SetToListProvider<Serialization>> literal = new TypeLiteral<SetToListProvider<Serialization>>() {};
		bind(new TypeLiteral<List<Serialization>>() {}).toProvider(literal);
		bind(new TypeLiteral<List<BeanValidator>>() {}).toInstance(Collections.<BeanValidator>emptyList());

	}

	private void requestInfoBindings() {
		bind(MutableRequest.class).toProvider(new Provider<MutableRequest>() {

			public MutableRequest get() {
				return VRaptorRequestHolder.currentRequest().getRequest();
			}
		}).in(GuiceProvider.REQUEST);

		bind(RequestInfo.class).toProvider(new Provider<RequestInfo>() {

			public RequestInfo get() {
				return VRaptorRequestHolder.currentRequest();
			}
		}).in(GuiceProvider.REQUEST);

		bind(HttpSession.class).toProvider(new Provider<HttpSession>() {

			public HttpSession get() {
				return VRaptorRequestHolder.currentRequest().getRequest().getSession();
			}
		}).in(GuiceProvider.REQUEST);
		bind(MutableResponse.class).toProvider(new Provider<MutableResponse>() {

			public MutableResponse get() {
				return VRaptorRequestHolder.currentRequest().getResponse();
			}
		}).in(GuiceProvider.REQUEST);
		bind(HttpServletResponse.class).to(MutableResponse.class).in(GuiceProvider.REQUEST);
		bind(HttpServletRequest.class).to(MutableRequest.class).in(GuiceProvider.REQUEST);
		bind(ServletContext.class).toInstance(context);
	}

	private Matcher<TypeLiteral<?>> type(final Matcher<? super Class> matcher) {
		return new AbstractMatcher<TypeLiteral<?>>() {
			public boolean matches(TypeLiteral<?> literal) {
				return matcher.matches(literal.getRawType());
			}
		};
	}

}
