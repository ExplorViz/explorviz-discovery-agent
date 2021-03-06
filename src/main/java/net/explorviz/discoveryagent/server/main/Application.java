package net.explorviz.discoveryagent.server.main;

import javax.ws.rs.ApplicationPath;
import net.explorviz.discoveryagent.server.filters.CorsResponseFilter;
import net.explorviz.discoveryagent.services.TypeService;
import net.explorviz.shared.discovery.exceptions.mapper.procezz.ProcezzGenericMapper;
import net.explorviz.shared.discovery.exceptions.mapper.procezz.ProcezzManagementTypeIncompatibleMapper;
import net.explorviz.shared.discovery.exceptions.mapper.procezz.ProcezzManagementTypeNotFoundMapper;
import net.explorviz.shared.discovery.exceptions.mapper.procezz.ProcezzMonitoringSettingsMapper;
import net.explorviz.shared.discovery.exceptions.mapper.procezz.ProcezzNotFoundMapper;
import net.explorviz.shared.discovery.exceptions.mapper.procezz.ProcezzStartMapper;
import net.explorviz.shared.discovery.exceptions.mapper.procezz.ProcezzStopMapper;
import net.explorviz.shared.discovery.model.Agent;
import net.explorviz.shared.discovery.model.Procezz;
import org.glassfish.jersey.server.ResourceConfig;

@ApplicationPath("")
public class Application extends ResourceConfig {

  public Application() {

    super();

    TypeService.typeMap.put("Agent", Agent.class);
    TypeService.typeMap.put("Procezz", Procezz.class);

    register(new DependencyInjectionBinder());

    register(SetupApplicationListener.class);

    register(CorsResponseFilter.class);

    // Exception Mapper
    register(ProcezzGenericMapper.class);
    register(ProcezzManagementTypeIncompatibleMapper.class);
    register(ProcezzManagementTypeNotFoundMapper.class);
    register(ProcezzMonitoringSettingsMapper.class);
    register(ProcezzNotFoundMapper.class);
    register(ProcezzStartMapper.class);
    register(ProcezzStopMapper.class);

    // provider
    packages("net.explorviz.discoveryagent.server.provider");

    // register core resources
    packages("net.explorviz.discoveryagent.server.resources");
  }
}
