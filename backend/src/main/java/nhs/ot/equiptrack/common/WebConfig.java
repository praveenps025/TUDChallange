package nhs.ot.equiptrack.common;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

// One app, one address: the API under /api and the React app (built into the
// jar's static folder) everywhere else, so the camera and sign-in work on a
// single HTTPS origin.
@Configuration
class WebConfig implements WebMvcConfigurer {

	@Override
	public void configurePathMatch(PathMatchConfigurer configurer) {
		configurer.addPathPrefix("/api", HandlerTypePredicate.forAnnotation(RestController.class)
				.and(HandlerTypePredicate.forBasePackage("nhs.ot.equiptrack")));
	}

	// Client-side routes such as /manage/inventory get index.html so a refresh
	// or a shared link opens the app. Unknown /api paths and missing files stay 404.
	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/**")
				.addResourceLocations("classpath:/static/")
				.resourceChain(true)
				.addResolver(new PathResourceResolver() {
					@Override
					protected Resource getResource(String path, Resource location) throws IOException {
						Resource requested = path.isEmpty() || path.endsWith("/") ? null : location.createRelative(path);
						if (requested != null && requested.exists() && requested.isReadable()) {
							return requested;
						}
						String lastSegment = path.substring(path.lastIndexOf('/') + 1);
						if (path.equals("api") || path.startsWith("api/") || lastSegment.contains(".")) {
							return null;
						}
						Resource index = location.createRelative("index.html");
						return index.exists() ? index : null;
					}
				});
	}

}
