package org.apache.wicket.examples;

import de.agilecoders.wicket.core.Bootstrap;
import de.agilecoders.wicket.core.settings.BootstrapSettings;
import de.agilecoders.wicket.themes.markup.html.bootswatch.BootswatchTheme;
import de.agilecoders.wicket.themes.markup.html.bootswatch.BootswatchThemeProvider;
import net.ftlines.wicketsource.WicketSource;
import org.apache.wicket.Application;
import org.apache.wicket.protocol.http.WebApplication;

/**
 *
 */
public class ExampleApplicationConfigurer
{
	public static void configure(WebApplication application)
	{

		if (application.usesDevelopmentConfig()) {
			WicketSource.configure(application);
		}

		BootstrapSettings bootstrapSettings = new BootstrapSettings();
		bootstrapSettings.setThemeProvider(new BootswatchThemeProvider(BootswatchTheme.Flatly));
		Bootstrap.install(application, bootstrapSettings);
	}
}
