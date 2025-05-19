package com.elicitsoftware.admin;

import com.elicitsoftware.model.User;
import jakarta.enterprise.context.RequestScoped;


@RequestScoped
public class Service {

    public User Login(String username) {
        try {
            User user = User.find("username", username).firstResult();
            return user;
        } catch (Exception e) {
            return null;
        }
    }
}
