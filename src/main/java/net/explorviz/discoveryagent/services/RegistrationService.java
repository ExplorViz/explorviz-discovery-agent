package net.explorviz.discoveryagent.services;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.jasminb.jsonapi.ResourceConverter;

import net.explorviz.discovery.exceptions.GenericNoConnectionException;
import net.explorviz.discovery.exceptions.procezz.ProcezzGenericException;
import net.explorviz.discovery.model.Agent;
import net.explorviz.discovery.services.ClientService;
import net.explorviz.discoveryagent.procezz.InternalRepository;
import net.explorviz.discoveryagent.procezz.ProcezzUtility;
import net.explorviz.discoveryagent.server.provider.JSONAPIProvider;
import net.explorviz.discoveryagent.util.ResourceConverterFactory;

public final class RegistrationService {

	private static final Logger LOGGER = LoggerFactory.getLogger(RegistrationService.class);

	private static final long REGISTRATION_TIMER_RATE = 60000;
	private static final long UPDATE_TIMER_RATE = 30000;

	private static AtomicBoolean registrationDone = new AtomicBoolean(false);

	private static boolean isHttpRequestSetupDone;

	private static Timer registrationTimer;
	private static Timer updateTimer;

	private static String explorVizUrl;
	private static ResourceConverter converter;
	private static ClientService clientService;

	private static Agent agent;

	private RegistrationService() {
		// don't instantiate
	}

	public static boolean isRegistrationDone() {
		return registrationDone.get();
	}

	private static void prepareHTTPRequest() {

		if (isHttpRequestSetupDone) {
			return;
		}

		clientService = new ClientService();

		converter = new ResourceConverterFactory().provide();

		clientService.registerProviderReader(new JSONAPIProvider<>(converter));
		clientService.registerProviderWriter(new JSONAPIProvider<>(converter));

		final String ip = PropertyService.getStringProperty("agentIP");
		final String userDefinedPort = PropertyService.getStringProperty("agentPort");
		final String embeddedGrettyPort = PropertyService.getStringProperty("httpPort");

		final String port = userDefinedPort.length() > 1 ? userDefinedPort : embeddedGrettyPort;

		explorVizUrl = PropertyService.getExplorVizBackendRootURL() + "/extension/discovery/agent";

		agent = new Agent(ip, port);
		agent.setId("placeholder");

		isHttpRequestSetupDone = true;

	}

	public static void callExplorVizBackend() {
		try {
			agent = clientService.postAgent(agent, explorVizUrl);
		} catch (ProcezzGenericException | GenericNoConnectionException e) {
			LOGGER.info(
					"Couldn't register agent at time: {}. Will retry in one minute. Backend offline or wrong backend IP? Check explorviz.properties file. Error: {}",
					new Date(System.currentTimeMillis()), e.toString());
			runRegistrationTimer(REGISTRATION_TIMER_RATE);
			return;
		}

		if (agent == null) {

			LOGGER.warn("Updated agent object was null. Will try to re-register.");

		} else {

			registrationTimer.cancel();
			registrationTimer.purge();

			LOGGER.info("Agent successfully registered");

			InternalRepository.agentObject = agent;

			// get new Ids for potential already discovered procezzes
			try {
				ProcezzUtility.getIdsForProcezzes(InternalRepository.getProcezzList());
				registrationDone.set(true);
				startUpdateService();
			} catch (ProcezzGenericException | GenericNoConnectionException e) {
				LOGGER.error(
						"Could not obtain unique IDs for procezzes. New procezzes WILL NOT be added to internal procezzlist Error: {}",
						e.getMessage());
				// Error occured, try to re-register again
				register();

			}
		}
	}

	public static void register() {

		if (updateTimer != null) {
			updateTimer.cancel();
			updateTimer.purge();
			LOGGER.info("Stopping UpdateService, because agent needs to re-register");
		}

		registrationDone.set(false);
		isHttpRequestSetupDone = false;

		registrationTimer = new Timer(true);
		runRegistrationTimer(0);
	}

	private static void runRegistrationTimer(final long scheduleDelay) {
		prepareHTTPRequest();

		final TimerTask registrationTask = new TimerTask() {

			@Override
			public void run() {
				if (!RegistrationService.isRegistrationDone()) {
					RegistrationService.callExplorVizBackend();
				}
			}
		};

		registrationTimer.schedule(registrationTask, scheduleDelay);
	}

	private static void startUpdateService() {

		LOGGER.info("Starting UpdateService");

		updateTimer = new Timer(true);

		final UpdateProcezzListService updateService = new UpdateProcezzListService();

		// refresh internal ProcessList every minute
		updateTimer.scheduleAtFixedRate(updateService, 0, UPDATE_TIMER_RATE);
	}

}
