package com.elicitsoftware.admin.flow;

/*-
 * ***LICENSE_START***
 * Elicit Admin
 * %%
 * Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.model.Department;
import com.elicitsoftware.model.Subject;
import com.elicitsoftware.model.User;
import com.elicitsoftware.test.DepartmentFixtures;
import com.elicitsoftware.test.PostgresTestResource;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.server.VaadinSession;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.enterprise.inject.spi.CDI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UC-003 BR-189 on the Register form: a first, middle or last name longer than 50 characters,
 * or an email address longer than 255, is a field error naming its limit, as the CSV import and
 * the API name it (UC-010 BR-188).
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class RegisterViewFieldLengthTest extends QuarkusBrowserlessTest {

    private static final String USERNAME = "register.lengths";

    private RegisterView view;

    @BeforeEach
    @TestSecurity(user = USERNAME, roles = {"elicit_user"})
    void setUp() {
        Department own = DepartmentFixtures.department(Long.toString(System.nanoTime(), 36));
        User user = DepartmentFixtures.consoleRecord(USERNAME, true, own);
        VaadinSession.getCurrent().setAttribute("user", user);

        view = CDI.current().select(RegisterView.class).get();
        UI.getCurrent().add(view);

        textField("register-first-name").setValue("Ann");
        textField("register-last-name").setValue("Lee");
        email().setValue("ann.lee@example.org");
    }

    private TextField textField(String id) {
        return find(TextField.class, view).all().stream()
                .filter(field -> id.equals(field.getId().orElse(null)))
                .findFirst().orElseThrow();
    }

    private EmailField email() {
        return find(EmailField.class, view).all().stream()
                .filter(field -> "register-email".equals(field.getId().orElse(null)))
                .findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private Binder<Subject> binder() throws Exception {
        Field field = RegisterView.class.getDeclaredField("binder");
        field.setAccessible(true);
        return (Binder<Subject>) field.get(view);
    }

    /**
     * The errors validation reports for the given fields. The department and survey choices are
     * left as the form opens, so the whole form's errors would include theirs.
     */
    @SafeVarargs
    private List<String> errorsOf(HasValue<?, ?>... fields) throws Exception {
        List<HasValue<?, ?>> wanted = List.of(fields);
        return binder().validate().getFieldValidationErrors().stream()
                .filter(status -> wanted.contains(status.getField()))
                .map(status -> status.getMessage().orElse(""))
                .toList();
    }

    private List<String> errorsWith(HasValue<?, String> field, String value) throws Exception {
        field.setValue(value);
        return errorsOf(field);
    }

    @Test
    @TestSecurity(user = USERNAME, roles = {"elicit_user"})
    void given_namesOfExactlyTheLimit_when_validated_then_noFieldIsInError() throws Exception {
        textField("register-first-name").setValue("F".repeat(50));
        textField("register-middle-name").setValue("M".repeat(50));
        textField("register-last-name").setValue("L".repeat(50));

        assertEquals(List.of(), errorsOf(textField("register-first-name"), textField("register-middle-name"),
                textField("register-last-name"), email()));
    }

    @Test
    @TestSecurity(user = USERNAME, roles = {"elicit_user"})
    void given_anOverlongFirstName_when_validated_then_itsLimitIsNamed() throws Exception {
        assertEquals(List.of("First name can be at most 50 characters"),
                errorsWith(textField("register-first-name"), "F".repeat(51)));
    }

    @Test
    @TestSecurity(user = USERNAME, roles = {"elicit_user"})
    void given_anOverlongLastName_when_validated_then_itsLimitIsNamed() throws Exception {
        assertEquals(List.of("Last name can be at most 50 characters"),
                errorsWith(textField("register-last-name"), "L".repeat(51)));
    }

    @Test
    @TestSecurity(user = USERNAME, roles = {"elicit_user"})
    void given_anOverlongMiddleName_when_validated_then_itsLimitIsNamed() throws Exception {
        assertEquals(List.of("Middle name can be at most 50 characters"),
                errorsWith(textField("register-middle-name"), "M".repeat(51)));
    }

    @Test
    @TestSecurity(user = USERNAME, roles = {"elicit_user"})
    void given_anOverlongEmail_when_validated_then_itsLimitIsNamed() throws Exception {
        String label = "d".repeat(63);
        String address = "a".repeat(60) + "@" + label + "." + label + "." + label + ".org";

        assertEquals(List.of("Email address can be at most 255 characters"), errorsWith(email(), address));
    }
}
