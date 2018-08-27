package org.diskproject.server;

import javax.annotation.PreDestroy;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.Path;
import javax.ws.rs.GET;

import org.diskproject.server.api.impl.DiskResource;
import org.diskproject.server.api.impl.UserResource;
import org.diskproject.server.filters.AcceptHeaderFilter;
import org.diskproject.server.filters.CORSResponseFilter;
import org.diskproject.server.filters.UserAuthenticationFilter;
import org.diskproject.server.repository.DiskRepository;
import org.diskproject.server.users.UserDatabase;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;

class DiskServer extends ResourceConfig {

  public DiskServer() {
	  System.out.println("me:"+this.toString());
    register(AcceptHeaderFilter.class);
    register(CORSResponseFilter.class);
    register(RolesAllowedDynamicFeature.class);
    register(UserAuthenticationFilter.class);
    register(UserResource.class);
    register(DiskResource.class);
  }

  @PreDestroy
  public void onDestroy() {
    // Cleanup tasks
    UserDatabase.shutdownDB();
    DiskRepository.get().shutdownExecutors();
  }

}
