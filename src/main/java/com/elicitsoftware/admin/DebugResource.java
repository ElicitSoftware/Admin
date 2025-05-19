package com.elicitsoftware.admin;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

// Uncomment out this next line for testing of the OIDC roles
//@Path("/debug")
@Produces(MediaType.TEXT_PLAIN)
public class DebugResource {

    @Inject
    SecurityIdentity identity;

    @GET
    public String showIdentity() {
        return "User: " + identity.getPrincipal().getName() +
                "\nRoles: " + identity.getRoles().toString();
    }
}