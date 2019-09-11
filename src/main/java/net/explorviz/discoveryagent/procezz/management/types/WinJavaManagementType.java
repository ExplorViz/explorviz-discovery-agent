package net.explorviz.discoveryagent.procezz.management.types;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.explorviz.discoveryagent.procezz.management.ProcezzManagementType;
import net.explorviz.discoveryagent.procezz.management.types.util.WinAbstraction;
import net.explorviz.discoveryagent.services.MonitoringFilesystemService;
import net.explorviz.shared.discovery.exceptions.mapper.ResponseUtil;
import net.explorviz.shared.discovery.exceptions.procezz.ProcezzManagementTypeIncompatibleException;
import net.explorviz.shared.discovery.exceptions.procezz.ProcezzNotFoundException;
import net.explorviz.shared.discovery.exceptions.procezz.ProcezzStartException;
import net.explorviz.shared.discovery.exceptions.procezz.ProcezzStopException;
import net.explorviz.shared.discovery.model.Agent;
import net.explorviz.shared.discovery.model.Procezz;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PMT implementation for compabilitiy with windows.
 *
 */
public class WinJavaManagementType implements ProcezzManagementType {
  public static final String USE_OS_FLAG = "Use-OS-Exec-CMD";
  private static final Logger LOGGER = LoggerFactory.getLogger(WinJavaManagementType.class);
  private static final String EXPLORVIZ_MODEL_ID_FLAG = "-Dexplorviz.agent.model.id=";

  private static final String SPACE_SYMBOL = " ";
  private static final String SKIP_DEFAULT_AOP =
      "-Dkieker.monitoring.skipDefaultAOPConfiguration=true";
  private static final String EXPORVIZ_MODEL_ID_FLAG_REGEX =
      "\\s" + EXPLORVIZ_MODEL_ID_FLAG + "([^\\s]+)";
  private static final String REGEX = "\\s+";
  private static final String REGEX_QUOTE = "\"";


  private final MonitoringFilesystemService monitoringFsService;

  public WinJavaManagementType(final MonitoringFilesystemService monitoringFsService) {
    this.monitoringFsService = monitoringFsService;
  }


  @Override
  public List<Procezz> getProcezzListFromOsAndSetAgent(final Agent agent) {

    final List<Procezz> procezzList = new ArrayList<>();

    final AtomicLong placeholderId = new AtomicLong(0);

    try {
      WinAbstraction.findProzzeses().forEach((pid, execCmd) -> {

        final Procezz p = new Procezz(pid, execCmd);

        p.setId(String.valueOf(placeholderId.incrementAndGet()));

        setWorkingDirectory(p);
        setProgrammingLanguage(p);

        if (agent != null) {
          p.setAgent(agent);
        }
        // Descriptor is needed for procezz to get the correct
        // procezzManagementType for starting, killing, restarting
        p.setProcezzManagementType(getManagementTypeDescriptor());

        procezzList.add(p);

      });
    } catch (final IOException e) {
      LOGGER.error("Error when finding procezzes: {}", e);

      return new ArrayList<Procezz>();
    }

    return procezzList;
  }

  @Override
  public void setWorkingDirectory(final Procezz procezz) {

    procezz.setWorkingDirectory("");


  }

  @Override
  public void startProcezz(final Procezz procezz)
      throws ProcezzStartException, ProcezzNotFoundException {

    LOGGER.info("Restarting procezz with ID:{}", procezz.getId());
    try {

      WinAbstraction.startProcessCmd(procezz.getAgentExecutionCommand());

    } catch (final IOException e) {
      LOGGER.error("Error during procezz start. Exception: {}", e);
      throw new ProcezzStartException(ResponseUtil.ERROR_PROCEZZ_START, e, procezz);
    }

  }

  @Override
  public void killProcezz(final Procezz procezz) throws ProcezzStopException {

    WinAbstraction.killProcessPid(procezz.getPid());

  }

  @Override
  public String getManagementTypeDescriptor() {
    return "WinJava";
  }

  @Override
  public void setProgrammingLanguage(final Procezz procezz) {
    procezz.setProgrammingLanguage(getProgrammingLanguage());

  }

  @Override
  public String getProgrammingLanguage() {
    return "java";
  }

  @Override
  public String getOsType() {
    return "windows";
  }


  @Override
  public void injectMonitoringAgentInProcezz(final Procezz procezz) throws ProcezzStartException {
    final String userExecCmd = procezz.getUserExecutionCommand();

    final boolean useUserExec = userExecCmd != null && userExecCmd.length() > 0 ? true : false;

    final String execPath = useUserExec ? userExecCmd : procezz.getOsExecutionCommand();
    final String execPathWithoutAgentFlag = execPath.replaceFirst(EXPORVIZ_MODEL_ID_FLAG_REGEX, "");
    try {
      final String[] execPathFragments = splitter(execPathWithoutAgentFlag);
      final String completeKiekerCommand = prepareMonitoringJvmarguments(procezz.getId());

      final String newExecCommand = execPathFragments[0] + SPACE_SYMBOL + completeKiekerCommand
          + procezz.getId() + SPACE_SYMBOL + execPathFragments[1];
      procezz.setAgentExecutionCommand(newExecCommand);
    } catch (final IndexOutOfBoundsException | MalformedURLException e) {
      throw new ProcezzStartException(ResponseUtil.ERROR_AGENT_FLAG_DETAIL, e, procezz);
    }


  }

  @Override
  public void injectProcezzIdentificationProperty(final Procezz procezz)
      throws ProcezzStartException {
    final String userExecCmd = procezz.getUserExecutionCommand();
    final boolean useuserExecCmd = userExecCmd != null && userExecCmd.length() > 0 ? true : false;
    final String execPath =
        useuserExecCmd ? procezz.getUserExecutionCommand() : procezz.getOsExecutionCommand();
    final String execPathWithoutAgentFlag = execPath.replaceFirst(EXPORVIZ_MODEL_ID_FLAG_REGEX, "");

    try {
      final String[] execPathFragments = splitter(execPathWithoutAgentFlag);
      final String newExecCommand = execPathFragments[0] + SPACE_SYMBOL + EXPLORVIZ_MODEL_ID_FLAG
          + procezz.getId() + SPACE_SYMBOL + execPathFragments[1];

      procezz.setAgentExecutionCommand(newExecCommand);
    } catch (final IndexOutOfBoundsException e) {
      throw new ProcezzStartException(ResponseUtil.ERROR_AGENT_FLAG_DETAIL, e, procezz);
    }

  }

  @Override
  public void removeMonitoringAgentInProcezz(final Procezz procezz) throws ProcezzStartException {

    final String userExecCmd = procezz.getUserExecutionCommand();

    final boolean useUserExec = userExecCmd != null && userExecCmd.length() > 0 ? true : false;

    final String execPath =
        useUserExec ? procezz.getUserExecutionCommand() : procezz.getOsExecutionCommand();

    final String execPathWithoutAgentFlag = execPath.replaceFirst(EXPORVIZ_MODEL_ID_FLAG_REGEX, "");
    procezz.setAgentExecutionCommand(execPathWithoutAgentFlag);


  }

  /**
   * Prepares String with the paths to the kieker-jar, kieker.monitoring.properties and aop.xml.
   *
   * @param entityID of a process.
   * @returns preparted String.
   * @throws MalformedURLException in case, that the kieker.monitoring.properties or the aop.xml
   *         does not exist for a given entityID.
   */
  private String prepareMonitoringJvmarguments(final String entityId) throws MalformedURLException {

    final String kiekerJarPath = monitoringFsService.getKiekerJarPath();
    final String javaagentPart = "-javaagent:" + kiekerJarPath;

    final String kiekerConfigPath = monitoringFsService.getKiekerConfigPathForProcezzID(entityId);
    final String kiekerConfigPart = "-Dkieker.monitoring.configuration=" + kiekerConfigPath;

    final String aopConfigPath = monitoringFsService.getAopConfigPathForProcezzID(entityId);
    final String aopConfigPart =
        "-Dorg.aspectj.weaver.loadtime.configuration=file://" + aopConfigPath;

    return javaagentPart + SPACE_SYMBOL + kiekerConfigPart + SPACE_SYMBOL + aopConfigPart
        + SPACE_SYMBOL + SKIP_DEFAULT_AOP + SPACE_SYMBOL + EXPLORVIZ_MODEL_ID_FLAG;
  }


  @Override
  public boolean compareProcezzesByIdentificationProperty(final Procezz p1, final Procezz p2)
      throws ProcezzManagementTypeIncompatibleException {
    if (!p1.getProcezzManagementType().equals(p2.getProcezzManagementType())) {
      throw new ProcezzManagementTypeIncompatibleException(
          ResponseUtil.ERROR_PROCEZZ_TYPE_INCOMPATIBLE_COMP, new Exception());
    }
    return p2.getOsExecutionCommand().contains(EXPLORVIZ_MODEL_ID_FLAG + p1.getId());

  }

  /**
   * If you run a java process via CLI, windows replaces in some cases "java" with the path to the
   * executable of java in a string. THe risk is to have spaces in the path. So if we split the path
   * between the first occurring whitespace, its not guaranteed, that we have the java launcher on
   * the left and the rest of the launch on the right. Therefore, we try to detect if the first part
   * of the exec cmd is a string a or not.
   *
   *
   *
   * @param splitCmd the cmd to be splitted.
   * @return the splitted cmd.
   */
  public String[] splitter(final String splitCmd) throws IndexOutOfBoundsException {

    if (splitCmd.startsWith(REGEX_QUOTE)) {
      final String[] execPathFragments = splitCmd.split(REGEX_QUOTE, 3);
      final String[] execPathFragmentsRes = new String[2];
      execPathFragmentsRes[0] = REGEX_QUOTE + execPathFragments[1] + REGEX_QUOTE;
      execPathFragmentsRes[1] = execPathFragments[2].replaceFirst(REGEX, "");
      return execPathFragmentsRes;



    } else {
      return splitCmd.split(REGEX, 2);
    }

  }

}
