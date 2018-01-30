package net.explorviz.discoveryagent.server.main;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.explorviz.discovery.model.Agent;
import net.explorviz.discovery.model.ErrorObject;
import net.explorviz.discovery.model.Procezz;
import net.explorviz.discoveryagent.services.NotifyService;
import net.explorviz.discoveryagent.util.TypeService;

@WebListener
public class SetupListener implements ServletContextListener {

	private static final Logger LOGGER = LoggerFactory.getLogger(SetupListener.class);

	@Override
	public void contextInitialized(final ServletContextEvent servletContextEvent) {

		LOGGER.info("\n");
		LOGGER.info("* * * * * * * * * * * * * * * * * * *");
		LOGGER.info("Server started.");
		LOGGER.info("* * * * * * * * * * * * * * * * * * *\n");

		TypeService.typeMap.put("Agent", Agent.class);
		TypeService.typeMap.put("Procezz", Procezz.class);
		TypeService.typeMap.put("ErrorObject", ErrorObject.class);

		// register at backend
		new Thread(() -> {
			NotifyService.registerAgent();
		}).start();

	}

	@Override
	public void contextDestroyed(final ServletContextEvent servletContextEvent) {
		// Nothing to destroy
	}

}