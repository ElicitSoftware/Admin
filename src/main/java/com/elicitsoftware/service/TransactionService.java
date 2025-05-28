package com.elicitsoftware.service;

import com.elicitsoftware.model.Subject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class TransactionService {

    @Transactional
    public Subject saveSubject(Subject subject) {
        subject.persist();
        return subject;
    }
}
