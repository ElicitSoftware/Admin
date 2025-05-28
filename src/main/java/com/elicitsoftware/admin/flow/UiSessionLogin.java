package com.elicitsoftware.admin.flow;

import com.elicitsoftware.model.User;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.quarkus.annotation.UIScoped;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.persistence.Transient;

import java.io.Serializable;

@UIScoped
public class UiSessionLogin implements Serializable {

    @Inject
    SecurityIdentity identity;

    @PostConstruct
    public void init() {
        System.out.println("Initializing UI " + identity.getPrincipal().getName());
        // This runs once per UI session (browser tab/window)
        User user = User.find("username", identity.getPrincipal().getName()).firstResult();
        VaadinSession.getCurrent().setAttribute("user", user);
    }

    @Transient
    public User getUser() {
        return (User) VaadinSession.getCurrent().getAttribute("user");
    }
}