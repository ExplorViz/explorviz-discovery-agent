package net.explorviz.discoveryagent.procezz;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.explorviz.discovery.exceptions.mapper.ResponseUtil;
import net.explorviz.discovery.exceptions.procezz.ProcezzManagementTypeNotFoundException;
import net.explorviz.discovery.exceptions.procezz.ProcezzMonitoringSettingsException;
import net.explorviz.discovery.exceptions.procezz.ProcezzNotFoundException;
import net.explorviz.discovery.exceptions.procezz.ProcezzStartException;
import net.explorviz.discovery.exceptions.procezz.ProcezzStopException;
import net.explorviz.discovery.model.Agent;
import net.explorviz.discovery.model.Procezz;
import net.explorviz.discoveryagent.procezz.management.ProcezzManagementType;
import net.explorviz.discoveryagent.procezz.management.ProcezzManagementTypeFactory;

public final class InternalRepository {

	public static Agent agentObject;

	private static final Logger LOGGER = LoggerFactory.getLogger(InternalRepository.class);

	private static List<Procezz> internalProcezzList = new ArrayList<Procezz>();

	private InternalRepository() {
		// do not instantiate
	}

	public static List<Procezz> getProcezzList() {
		return internalProcezzList;
	}

	public static void updateInternalProcezzList() throws ProcessingException, WebApplicationException {
		synchronized (internalProcezzList) {
			mergeProcezzListWithInternalList(getNewProcezzesFromOS());
		}
	}

	public static Procezz updateRestartedProcezz(final Procezz oldProcezz) throws ProcezzNotFoundException {

		final String entityID = oldProcezz.getId();

		Procezz possibleRestartedProcezz = null;
		try {
			possibleRestartedProcezz = ProcezzUtility.findFlaggedProcezzInList(entityID, getNewProcezzesFromOS());
		} catch (final ProcezzNotFoundException e) {
			throw new ProcezzNotFoundException(ResponseUtil.ERROR_PROCEZZ_START_NOT_FOUND, e, oldProcezz);
		}

		final Procezz internalProcezz = findProcezzByID(oldProcezz.getId());

		// update pid and osExecCMD
		internalProcezz.setPid(possibleRestartedProcezz.getPid());
		internalProcezz.setAgentExecutionCommand(possibleRestartedProcezz.getOsExecutionCommand());
		internalProcezz.setLastDiscoveryTime(System.currentTimeMillis());

		// reset possible error state (user restarted crashed procezz)
		internalProcezz.setErrorOccured(false);
		internalProcezz.setErrorMessage(null);

		return internalProcezz;

	}

	public static List<Procezz> getNewProcezzesFromOS() {

		if (agentObject == null) {
			LOGGER.warn("No agent object in internal repository. The agent will not detect procezzes");
			return new ArrayList<Procezz>();
		}

		final List<Procezz> newOSProcezzList = new ArrayList<Procezz>();

		// Take every managementType and let them fetch the procezzLists
		for (final ProcezzManagementType managementType : ProcezzManagementTypeFactory.getAllProcezzManagementTypes()) {
			newOSProcezzList.addAll(managementType.getProcezzListFromOSAndSetAgent(agentObject));
		}

		return newOSProcezzList;
	}

	public static void mergeProcezzListWithInternalList(final List<Procezz> newProcezzListFromOS) {

		// newProcezzListFromOS may contain duplicates, since multiple managementTypes
		// may find the same OS process
		final List<Procezz> newProcezzListWithoutDuplicates = removeDuplicatesInProcezzList(newProcezzListFromOS);

		synchronized (internalProcezzList) {

			LOGGER.info("Updating procezzList at: {}", new Date());

			// Check if already obtained PIDs are still in the new obtained procezzList
			final List<Procezz> stoppedProcezzes = getStoppedProcezzesOfInternalList(newProcezzListWithoutDuplicates);

			// Check if a running procezz was restarted by agent
			// and update old procezz entity
			updateStoppedProcezzes(stoppedProcezzes, newProcezzListWithoutDuplicates);

			// finally, add new-found (= remaining) procezzes to the internal storage
			ProcezzUtility.initializeAndAddNewProcezzes(newProcezzListWithoutDuplicates);

		}

	}

	public static List<Procezz> removeDuplicatesInProcezzList(final List<Procezz> newProcezzListFromOS) {
		return new ArrayList<Procezz>(new HashSet<Procezz>(newProcezzListFromOS));
	}

	private static boolean updateStoppedProcezzes(final List<Procezz> stoppedProcezzes,
			final List<Procezz> newProcezzListFromOS) {

		boolean unexpectedStoppedProcezzFound = false;

		for (final Procezz procezz : stoppedProcezzes) {

			// Every execCMD of a restarted procezz has a unique explorviz flag
			final Procezz possibleProcezz = findProcezzInListByExecCMD(procezz.getUserExecutionCommand(),
					newProcezzListFromOS);

			if (possibleProcezz == null) {
				// Procezz loss

				if (!procezz.isStopped()) {
					// Unexpected Procezz loss
					// that was not already discovered
					procezz.setStopped(true);
					procezz.setErrorOccured(true);
					procezz.setErrorMessage(
							"Procezz could not be found in latest procezzList. Maybe an error occured.");
					unexpectedStoppedProcezzFound = true;
				}

			} else {
				// Procezz has been restarted correctly

				procezz.setStopped(false);
				procezz.setErrorOccured(false);
				procezz.setErrorMessage(null);

				procezz.setPid(possibleProcezz.getPid());
				procezz.setUserExecutionCommand(possibleProcezz.getOsExecutionCommand());
				procezz.setMonitoredFlag(true);

				newProcezzListFromOS.remove(possibleProcezz);
			}
		}

		return unexpectedStoppedProcezzFound;
	}

	private static List<Procezz> getStoppedProcezzesOfInternalList(final List<Procezz> newProcezzList) {

		final List<Procezz> stoppedProcezzes = new ArrayList<Procezz>();

		synchronized (internalProcezzList) {

			for (final Procezz procezz : internalProcezzList) {

				final Procezz possibleProcezz = findProcezzInListByPID(procezz.getPid(), newProcezzList);

				if (possibleProcezz == null) {
					// Procezz not found in latest OS list = Old procezz, maybe restarted
					stoppedProcezzes.add(procezz);
				} else {
					// Procezz is still running
					possibleProcezz.setStopped(false);
					newProcezzList.remove(possibleProcezz);
				}

				procezz.setAgent(agentObject);
			}
		}

		return stoppedProcezzes;

	}

	private static Procezz findProcezzInListByExecCMD(final String userExecutionCommand,
			final List<Procezz> procezzList) {
		for (final Procezz possibleProcezz : procezzList) {

			final String osExecCMD = possibleProcezz.getOsExecutionCommand();

			if (userExecutionCommand != null && userExecutionCommand.equals(osExecCMD)) {
				return possibleProcezz;
			}

		}

		return null;
	}

	private static Procezz findProcezzInListByPID(final long PID, final List<Procezz> procezzList) {

		synchronized (internalProcezzList) {

			for (final Procezz possibleProcezz : procezzList) {

				final long tempPID = possibleProcezz.getPid();

				if (PID == tempPID) {
					return possibleProcezz;
				}

			}

		}

		return null;
	}

	public static Procezz findProcezzByID(final String id) throws ProcezzNotFoundException {
		synchronized (internalProcezzList) {
			final Procezz procezzInCache = internalProcezzList.stream().filter(Objects::nonNull)
					.filter(p -> p.getId().equals(id)).findFirst().orElse(null);

			if (procezzInCache == null) {
				throw new ProcezzNotFoundException(ResponseUtil.ERROR_PROCEZZ_ID_NOT_FOUND, new Exception());
			}

			return procezzInCache;
		}
	}

	public static Procezz handleProcezzPatchRequest(final Procezz procezz)
			throws ProcezzNotFoundException, ProcezzMonitoringSettingsException, ProcezzManagementTypeNotFoundException,
			ProcezzStopException, ProcezzStartException {

		synchronized (internalProcezzList) {

			final Procezz procezzInCache = findProcezzByID(procezz.getId());

			ProcezzUtility.copyProcezzAttributeValues(procezz, procezzInCache);

			if (procezzInCache.isStopped()) {
				ProcezzUtility.handleStop(procezzInCache);
			}

			if (procezzInCache.isRestart()) {
				return ProcezzUtility.handleRestart(procezzInCache);
			}

			return procezzInCache;
		}
	}

	public static Agent updateAgentProperties(final Agent agent) {

		synchronized (internalProcezzList) {
			agentObject.setName(agent.getName());
			agentObject.setHidden(agent.isHidden());
		}

		return agentObject;

	}

}
