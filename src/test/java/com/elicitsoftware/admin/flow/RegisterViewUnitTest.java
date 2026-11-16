package com.elicitsoftware.admin.flow;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.model.Department;
import com.elicitsoftware.model.Status;
import com.elicitsoftware.model.Subject;
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.request.AddRequest;
import com.elicitsoftware.response.AddResponse;
import com.elicitsoftware.response.AddResponseStatus;
import com.elicitsoftware.rest.AccessCodeService;
import com.elicitsoftware.security.DepartmentScope;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.UploadI18N;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.NavigationEvent;
import com.vaadin.flow.router.NavigationTrigger;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.RouterLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import static com.elicitsoftware.admin.flow.ResultDialogSupport.detailsIn;
import static com.elicitsoftware.admin.flow.ResultDialogSupport.errorsIn;
import static com.elicitsoftware.admin.flow.ResultDialogSupport.headingsIn;
import static com.elicitsoftware.admin.flow.ResultDialogSupport.messagesIn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-003: Register or Update a Subject (A6 the CSV import summary, BR-009 required fields) and
 * UC-019's empty state (BR-076 to BR-078) — {@link RegisterView} built with {@code new} in a plain
 * browserless UI.
 * <p>
 * Once a user is signed in, {@code init()} lists the surveys with a Panache query before it builds
 * the form, so the form itself is out of reach here. What is reached: the no-user notice, the
 * entry handling that needs no subject lookup, the validation guard of save and update, the CSV
 * import through a stand-in {@link AccessCodeService}, and -- through reflection, as nothing else
 * can call them -- the private builders of the survey and department pickers, the date picker and
 * upload texts, and the CSV and REST instructions.
 */
class RegisterViewUnitTest extends PlainAdminViewTestSupport {

    private Login login;
    private Presence presence;
    private Function<AddRequest, AddResponse> putSubject;
    private final List<AddRequest> requests = new ArrayList<>();
    private RegisterView view;

    /** Department ids the signed-in user is not assigned to in the database (NFR-021). */
    private final List<Long> foreignDepartments = new ArrayList<>();
    /** The stored subjects, by record id, that the view's lookup finds. */
    private final java.util.Map<Long, Subject> stored = new java.util.HashMap<>();
    /** Subjects the view wrote over their stored row. */
    private final List<Subject> merged = new ArrayList<>();

    private final Department oncology = department(3, "Oncology");
    private final Department clinic = department(7, "Clinic");

    @BeforeEach
    void stubs() {
        login = new Login();
        presence = new Presence();
        putSubject = request -> created(request.xid, request.firstName, request.lastName, "AC-" + request.xid);
    }

    private RegisterView newView(String... roles) {
        view = new RegisterView();
        view.subjects = new RegisterView.SubjectStore() {
            @Override
            Subject find(long id) {
                return stored.get(id);
            }

            @Override
            Subject merge(Subject edited) {
                merged.add(edited);
                return edited;
            }
        };
        view.uiSessionLogin = login(login);
        view.identity = identity("register.tester", roles);
        view.surveyPresence = presence(presence);
        view.departmentScope = new DepartmentScope() {
            @Override
            public boolean permits(long departmentId) {
                return !foreignDepartments.contains(departmentId);
            }
        };
        view.accessCodeService = new AccessCodeService() {
            @Override
            public AddResponse registerSubject(AddRequest request) {
                requests.add(request);
                return putSubject.apply(request);
            }

            @Override
            public boolean permitsDepartment(long departmentId) {
                return !foreignDepartments.contains(departmentId);
            }
        };
        UI.getCurrent().add(view);
        return view;
    }

    /** A view whose {@code init()} would have passed the no-user guard. */
    private RegisterView signedIn(String... roles) {
        newView(roles);
        setField(view, "user", user("register.tester", oncology, clinic));
        return view;
    }

    private static AddResponse created(String xid, String first, String last, String accessCode) {
        Status status = new Status();
        status.setXid(xid);
        status.setFirstName(first);
        status.setLastName(last);
        status.setAccessCode(accessCode);
        return response(new AddResponseStatus(status, "New Subject"));
    }

    private static AddResponse response(AddResponseStatus status) {
        AddResponse response = new AddResponse();
        response.addStatus(status);
        return response;
    }

    private static BeforeEnterEvent enter(String pathAndQuery) {
        UI ui = UI.getCurrent();
        NavigationEvent navigation = new NavigationEvent(ui.getInternals().getRouter(), new Location(pathAndQuery), ui,
                NavigationTrigger.PROGRAMMATIC);
        return new BeforeEnterEvent(navigation, RegisterView.class, List.of());
    }

    /** An entry by the edit route, {@code register/<id>} (UC-003 A5). */
    private static BeforeEnterEvent enterToEdit(String id) {
        UI ui = UI.getCurrent();
        NavigationEvent navigation = new NavigationEvent(ui.getInternals().getRouter(), new Location("register/" + id), ui,
                NavigationTrigger.PROGRAMMATIC);
        return new BeforeEnterEvent(navigation, RegisterView.class,
                new RouteParameters(RegisterView.ID_PARAMETER, id), List.of());
    }

    private Subject storedSubject(long id, long departmentId) {
        Subject subject = new Subject();
        subject.setId(id);
        subject.setDepartmentId(departmentId);
        subject.setFirstName("Ann");
        subject.setLastName("Lee");
        stored.put(id, subject);
        return subject;
    }

    /** Save and Update as {@code init()} leaves them: Save shown, Update hidden. */
    private Button[] buttons() {
        Button save = new Button();
        Button update = new Button();
        update.setVisible(false);
        setField(view, "saveButton", save);
        setField(view, "updateButton", update);
        return new Button[]{save, update};
    }

    /** A binder that always validates and writes the given department into the subject. */
    private static Binder<Subject> binderChoosing(long departmentId) {
        Binder<Subject> binder = new Binder<>(Subject.class);
        TextField department = new TextField();
        department.setValue(Long.toString(departmentId));
        binder.forField(department).bind(s -> Long.toString(s.getDepartmentId()), (s, v) -> s.setDepartmentId(Long.parseLong(v)));
        return binder;
    }

    private void upload(String csv) {
        view.handleCsvUpload(csv.getBytes(StandardCharsets.UTF_8));
    }

    private Dialog onlyDialog() {
        List<Dialog> dialogs = $(Dialog.class).all();
        assertEquals(1, dialogs.size(), "exactly one result dialog");
        assertTrue(dialogs.get(0).isOpened());
        return dialogs.get(0);
    }

    // ---- no console record (UC-001 A1) -----------------------------------------------------------

    @Test
    void given_noConsoleRecord_when_init_then_theExplanationNamesThePrincipalAsText() {
        newView("elicit_user");

        view.init();

        List<Component> children = view.getChildren().toList();
        assertEquals(1, children.size());
        Div notice = assertInstanceOf(Div.class, children.get(0));
        List<Paragraph> paragraphs = find(Paragraph.class, notice).all();
        assertEquals(t("searchView.noUser.loggedIn"), paragraphs.get(0).getText());
        assertEquals(t("searchView.noUser.help"), paragraphs.get(2).getText());
        List<Span> spans = find(Span.class, paragraphs.get(1)).all();
        assertEquals(List.of(t("searchView.noUser.before"), "register.tester", t("searchView.noUser.after")),
                spans.stream().map(Span::getText).toList());
        assertTrue(spans.get(1).hasClassName("inline-name"));
    }

    @Test
    void given_noConsoleRecord_when_entering_then_nothingElseIsChecked() {
        newView("elicit_user");
        view.init();
        presence.installed = false;

        view.beforeEnter(enterToEdit("5"));

        assertEquals(0, presence.calls, "init already explained; the entry is left alone");
        assertEquals(1, view.getComponentCount());
    }

    @Test
    void given_theView_when_askedForItsTitle_then_theTranslatedTitleIsGiven() {
        newView();

        assertEquals(t("registerView.pageTitle"), view.getPageTitle());
    }

    // ---- entry without an access code (UC-003 main flow) ----------------------------------------

    @Test
    void given_editingButtonsShown_when_enteringWithoutAnAccessCode_then_registrationModeIsRestored() {
        signedIn("elicit_user");
        Button save = new Button();
        Button update = new Button();
        save.setVisible(false);
        update.setVisible(true);
        setField(view, "saveButton", save);
        setField(view, "updateButton", update);

        view.beforeEnter(enter("register"));

        assertTrue(save.isVisible());
        assertFalse(update.isVisible());
        assertEquals(1, presence.calls);
    }

    @Test
    void given_onlyOneButton_when_enteringWithoutAnAccessCode_then_itIsLeftAlone() {
        signedIn("elicit_user");
        Button save = new Button();
        save.setVisible(false);
        setField(view, "saveButton", save);

        view.beforeEnter(enter("register?other=1"));

        assertFalse(save.isVisible(), "both buttons must exist before either is toggled");
    }

    // ---- entry to edit a subject (UC-003 A5, A7; BR-138; NFR-022) ---------------------------------

    @Test
    void given_aSubjectInOneOfTheUsersDepartments_when_enteringByItsId_then_updateModeHoldsThatSubject() {
        signedIn("elicit_user");
        Subject ann = storedSubject(5, 3);
        Button[] buttons = buttons();

        view.beforeEnter(enterToEdit("5"));

        assertEquals(ann, view.currentSubject());
        assertFalse(buttons[0].isVisible(), "Save is hidden while editing");
        assertTrue(buttons[1].isVisible());
        assertTrue(notifications().isEmpty());
    }

    @Test
    void given_aSubjectInAnotherDepartment_when_enteringByItsId_then_itIsAnsweredAsNotFound() {
        signedIn("elicit_user");
        storedSubject(5, 8);
        foreignDepartments.add(8L);
        Button[] buttons = buttons();

        view.beforeEnter(enterToEdit("5"));

        assertEquals(List.of(t("registerView.error.subjectNotFound")), notifications(), "A7: as if it did not exist");
        assertEquals(0, view.currentSubject().getId(), "BR-138: nothing of the subject is loaded");
        assertTrue(view.currentSubject().getFirstName() == null || view.currentSubject().getFirstName().isEmpty());
        assertTrue(buttons[0].isVisible(), "the form stays in registration mode");
        assertFalse(buttons[1].isVisible());
    }

    @Test
    void given_anIdThatNamesNoSubjectOrIsNotANumber_when_entering_then_theNoticeNamesNoIdentifier() {
        signedIn("elicit_user");
        Button[] buttons = buttons();

        view.beforeEnter(enterToEdit("404"));
        view.beforeEnter(enterToEdit("ABC123DEF"));

        assertEquals(List.of("Subject not found.", "Subject not found."), notifications(),
                "NFR-022: the notice repeats nothing from the address");
        assertTrue(buttons[0].isVisible());
        assertFalse(buttons[1].isVisible());
    }

    @Test
    void given_anAccessCodeInTheQuery_when_entering_then_noSubjectIsLoaded() {
        signedIn("elicit_user");
        storedSubject(5, 3);
        Button[] buttons = buttons();

        view.beforeEnter(enter("register?accessCode=ABC123DEF"));

        assertEquals(0, view.currentSubject().getId(), "NFR-022: an access code in the address opens nothing");
        assertTrue(buttons[0].isVisible());
        assertFalse(buttons[1].isVisible());
        assertTrue(notifications().isEmpty());
    }

    // ---- department rule on save and update (UC-003 A7, BR-138) ----------------------------------

    @Test
    void given_aSubjectStoredInAnotherDepartment_when_updating_then_nothingIsWritten() throws Exception {
        signedIn("elicit_user");
        Subject held = storedSubject(5, 8);
        foreignDepartments.add(8L);
        setField(view, "subject", held);

        view.updateSubject(binderChoosing(3));

        assertEquals(List.of(t("registerView.error.notYourDepartment")), notifications());
        assertTrue(merged.isEmpty());
        assertEquals(8, held.getDepartmentId(), "the form was not even written into the subject");
    }

    @Test
    void given_aSubjectThatNoLongerExists_when_updating_then_nothingIsWritten() throws Exception {
        signedIn("elicit_user");
        Subject held = new Subject();
        held.setId(77);
        setField(view, "subject", held);

        view.updateSubject(binderChoosing(3));

        assertEquals(List.of(t("registerView.error.notYourDepartment")), notifications());
        assertTrue(merged.isEmpty());
    }

    @Test
    void given_aDepartmentTheUserIsNotAssignedTo_when_updatingIntoIt_then_nothingIsWritten() throws Exception {
        signedIn("elicit_user");
        setField(view, "subject", storedSubject(5, 3));
        foreignDepartments.add(8L);

        view.updateSubject(binderChoosing(8));

        assertEquals(List.of(t("registerView.error.notYourDepartment")), notifications());
        assertTrue(merged.isEmpty());
    }

    @Test
    void given_aSubjectAndADepartmentOfTheUsers_when_updating_then_theSubjectIsWritten() throws Exception {
        signedIn("elicit_user");
        Subject held = storedSubject(5, 3);
        setField(view, "subject", held);

        view.updateSubject(binderChoosing(7));

        assertEquals(List.of(t("registerView.subjectUpdated")), notifications());
        assertEquals(List.of(held), merged);
        assertEquals(7, held.getDepartmentId(), "moving between two of the user's own departments stays allowed");
    }

    @Test
    void given_aDepartmentTheUserIsNotAssignedTo_when_saving_then_nothingIsRegistered() throws Exception {
        signedIn("elicit_user");
        foreignDepartments.add(8L);

        view.saveSubject(binderChoosing(8));

        assertEquals(List.of(t("registerView.error.notYourDepartment")), notifications(),
                "BR-138: refused before the exclusion list or an access code is touched");
        assertTrue(requests.isEmpty());
    }

    // ---- missing survey (UC-019) -----------------------------------------------------------------

    @Test
    void given_noSurveyInstalled_when_enteringAsAnAdministrator_then_theEmptyStateLeadsWithALink() {
        signedIn("elicit_admin");
        view.add(new Div());
        presence.installed = false;

        view.beforeEnter(enter("register"));

        Component first = view.getComponentAt(0);
        assertEquals(MissingSurveyNotice.EMPTY_STATE_ID, first.getId().orElseThrow());
        assertEquals(1, find(RouterLink.class, first).all().size());
        assertEquals(2, view.getComponentCount(), "the view stays usable (BR-077)");
    }

    @Test
    void given_noSurveyInstalled_when_enteringAsAUser_then_theEmptyStateOffersNoLink() {
        signedIn("elicit_user");
        presence.installed = false;

        view.refreshMissingSurveyNotice();

        Component first = view.getComponentAt(0);
        assertEquals(MissingSurveyNotice.EMPTY_STATE_ID, first.getId().orElseThrow());
        assertTrue(find(RouterLink.class, first).all().isEmpty());
    }

    @Test
    void given_theEmptyStateShown_when_aSurveyIsInstalled_then_theNextEntryRemovesIt() {
        signedIn("elicit_user");
        presence.installed = false;
        view.refreshMissingSurveyNotice();
        view.refreshMissingSurveyNotice();
        assertEquals(1, view.getComponentCount(), "shown once however often the view is entered");

        presence.installed = true;
        view.refreshMissingSurveyNotice();

        assertEquals(0, view.getComponentCount());
    }

    // ---- save and update guard (BR-009) ----------------------------------------------------------

    private static Binder<Subject> invalidBinder() {
        Binder<Subject> binder = new Binder<>(Subject.class);
        binder.forField(new TextField()).asRequired("required").bind("firstName");
        return binder;
    }

    @Test
    void given_aFormFailingValidation_when_saving_then_theUserIsAskedToFixItAndNothingIsStored() throws Exception {
        signedIn("elicit_user");

        view.saveSubject(invalidBinder());

        assertEquals(List.of(t("registerView.error.fixValidation")), notifications());
        assertTrue(requests.isEmpty());
    }

    @Test
    void given_aFormFailingValidation_when_updating_then_theUserIsAskedToFixIt() throws Exception {
        signedIn("elicit_user");
        setField(view, "subject", storedSubject(5, 3));

        view.updateSubject(invalidBinder());

        assertEquals(List.of(t("registerView.error.fixValidation")), notifications());
    }

    // ---- CSV import (UC-003 A6) ------------------------------------------------------------------

    @Test
    void given_twoValidRows_when_uploading_then_theSummaryListsEachSubject() {
        signedIn("elicit_user");

        upload("#departmentId,firstName,lastName,middleName,dob,email,phone,xid\n"
                + "\n"
                + "3,Ann,Lee,,1990-01-15,ann@example.org,555-123-4567,X1\n"
                + "7,Bob,Ray,Q,12/10/1992,bob@example.org,,X2\n");

        Dialog dialog = onlyDialog();
        assertEquals(t("registerView.csvImport.successTitle"), dialog.getHeaderTitle());
        assertEquals(List.of(t("registerView.csvImport.summary.heading", 2)), headingsIn(dialog));
        String outcome = t("registerView.csvImport.outcome.new");
        assertEquals(List.of(
                t("registerView.csvImport.summary.subject", outcome, "Ann Lee ("
                        + t("registerView.csvImport.summary.xid", "X1") + "), "
                        + t("registerView.csvImport.summary.accessCode", "AC-X1")),
                t("registerView.csvImport.summary.subject", outcome, "Bob Ray ("
                        + t("registerView.csvImport.summary.xid", "X2") + "), "
                        + t("registerView.csvImport.summary.accessCode", "AC-X2"))), detailsIn(dialog));
        assertTrue(errorsIn(dialog).isEmpty());
        assertEquals(List.of(3, 7), requests.stream().map(r -> r.departmentId).toList());
    }

    @Test
    void given_oneValidRow_when_uploading_then_theHeadingIsSingular() {
        signedIn("elicit_user");

        upload("3,Ann,Lee,,,ann@example.org\n");

        assertEquals(List.of(t("registerView.csvImport.summary.headingOne")), headingsIn(onlyDialog()));
    }

    @Test
    void given_noRows_when_uploading_then_theSummaryCountsNone() {
        signedIn("elicit_user");

        upload("# only a comment\n");

        Dialog dialog = onlyDialog();
        assertEquals(List.of(t("registerView.csvImport.summary.heading", 0)), headingsIn(dialog));
        assertTrue(detailsIn(dialog).isEmpty());
    }

    @Test
    void given_rowsWithPartialDetails_when_uploading_then_eachLineSaysOnlyWhatIsKnown() {
        signedIn("elicit_user");
        putSubject = request -> switch (request.xid) {
            case "nameless" -> {
                Status status = new Status();
                status.setXid("NX");
                yield response(new AddResponseStatus(status, "Existing Subject"));
            }
            case "codeOnly" -> {
                Status status = new Status();
                status.setXid(" ");
                status.setAccessCode("ONLY");
                yield response(new AddResponseStatus(status, "Existing Subject"));
            }
            case "blank" -> {
                Status status = new Status();
                status.setAccessCode("");
                yield response(new AddResponseStatus(status, "Mystery"));
            }
            case "noStatus" -> response(new AddResponseStatus(null, "Exluded Subject"));
            case "lastOnly" -> {
                Status status = new Status();
                status.setLastName("Lee");
                yield response(new AddResponseStatus(status, null));
            }
            default -> throw new IllegalStateException(request.xid);
        };

        upload("3,A,B,,,a@x.org,,nameless\n3,A,B,,,a@x.org,,codeOnly\n3,A,B,,,a@x.org,,blank\n"
                + "3,A,B,,,a@x.org,,noStatus\n3,A,B,,,a@x.org,,lastOnly\n");

        String existing = t("registerView.csvImport.outcome.existing");
        assertEquals(List.of(
                t("registerView.csvImport.summary.subject", existing, "(" + t("registerView.csvImport.summary.xid", "NX") + ")"),
                t("registerView.csvImport.summary.subject", existing, t("registerView.csvImport.summary.accessCode", "ONLY")),
                "Mystery",
                t("registerView.csvImport.outcome.excluded"),
                t("registerView.csvImport.summary.subject", "", "Lee")), detailsIn(onlyDialog()));
    }

    @Test
    void given_spanishLocale_when_uploading_then_theOutcomesAreTranslated() {
        UI.getCurrent().setLocale(Locale.forLanguageTag("es-419"));
        signedIn("elicit_user");
        putSubject = request -> response(new AddResponseStatus(null, switch (request.xid) {
            case "n" -> "New Subject";
            case "e" -> "Existing Subject";
            default -> "Exluded Subject";
        }));

        upload("3,A,B,,,a@x.org,,n\n3,A,B,,,a@x.org,,e\n3,A,B,,,a@x.org,,x\n");

        assertEquals(List.of(spanish("registerView.csvImport.outcome.new"), spanish("registerView.csvImport.outcome.existing"),
                spanish("registerView.csvImport.outcome.excluded")), detailsIn(onlyDialog()));
    }

    @Test
    void given_theServiceReportsAnError_when_uploading_then_theSummaryAddsAnErrorsSection() {
        signedIn("elicit_user");
        putSubject = request -> {
            if ("bad".equals(request.xid)) {
                AddResponse failed = new AddResponse();
                failed.setError("department 3 does not exist");
                return failed;
            }
            return created(request.xid, "Ann", "Lee", "AC1");
        };

        upload("3,Ann,Lee,,,a@x.org,,ok\n3,Ann,Lee,,,a@x.org,,bad\n");

        Dialog dialog = onlyDialog();
        assertEquals(t("registerView.csvImport.successTitle"), dialog.getHeaderTitle());
        assertEquals(List.of(t("registerView.csvImport.summary.headingOne"), t("registerView.csvImport.errorsHeading")),
                headingsIn(dialog));
        assertEquals(List.of("department 3 does not exist"), errorsIn(dialog));
    }

    @Test
    void given_aRowForADepartmentTheUserIsNotAssignedTo_when_uploading_then_thatLineIsRefusedAndNotRegistered() {
        signedIn("elicit_user");
        foreignDepartments.add(8L);

        upload("3,Ann,Lee,,,a@x.org,,ok\n8,Bob,Ray,,,b@x.org,,other\n");

        Dialog dialog = onlyDialog();
        assertEquals(t("registerView.csvImport.errorTitle"), dialog.getHeaderTitle());
        assertEquals(List.of(t("definition.line", "2", t("csvImport.departmentNotPermitted", "8"))), errorsIn(dialog));
        assertEquals(List.of("ok"), requests.stream().map(r -> r.xid).toList(), "UC-010 BR-137: only the user's own department");
    }

    @Test
    void given_rejectedRows_when_uploading_then_theErrorDialogListsEachRejectedLine() {
        signedIn("elicit_user");

        upload("3,Ann,Lee,,,a@x.org\nnot,enough\nx,Ann,Lee,,,a@x.org\n");

        Dialog dialog = onlyDialog();
        assertEquals(t("registerView.csvImport.errorTitle"), dialog.getHeaderTitle());
        assertEquals(List.of(t("registerView.csvImport.lineErrorsHeading")), headingsIn(dialog));
        assertEquals(List.of(t("definition.line", "2", t("csvImport.badFormat")),
                t("definition.line", "3", t("csvImport.badDepartmentId"))), errorsIn(dialog));
    }

    @Test
    void given_anUnreadableUpload_when_uploading_then_theDialogSaysOnlyThatItFailed() {
        signedIn("elicit_user");

        view.handleCsvUpload(null);

        Dialog dialog = onlyDialog();
        assertEquals(t("registerView.csvImport.errorTitle"), dialog.getHeaderTitle());
        assertEquals(List.of("The file could not be imported. The details are in the application log."), messagesIn(dialog),
                "UC-010 BR-139: no exception text");
    }

    // ---- the form's private builders (reached only past init's Panache query) -------------------

    @Test
    void given_installedSurveys_when_buildingThePicker_then_theyAreListedByName() {
        signedIn("elicit_user");
        Survey census = new Survey();
        census.id = 1;
        census.name = "Census";

        ComboBox<Survey> picker = invoke(view, "getSurveyComboBox", new Class<?>[] {List.class}, List.of(census));

        assertEquals(t("registerView.survey"), picker.getLabel());
        assertEquals(List.of(census), picker.getListDataView().getItems().toList());
        assertEquals("Census", picker.getItemLabelGenerator().apply(census));
        assertTrue(picker.getThemeNames().contains("small"));
    }

    @Test
    void given_theUsersDepartments_when_buildingThePicker_then_onlyTheyAreListedByName() {
        signedIn("elicit_user");

        ComboBox<Department> picker = invoke(view, "getDepartmentComboBox", new Class<?>[0]);

        assertEquals(t("registerView.department"), picker.getLabel());
        assertEquals(List.of(oncology, clinic), picker.getListDataView().getItems().toList());
        assertEquals("Clinic", picker.getItemLabelGenerator().apply(clinic));
        assertTrue(picker.getThemeNames().contains("small"));
    }

    @Test
    void given_englishLocale_when_buildingTheDatePickerTexts_then_monthsAndWeekdaysComeFromTheLocale() {
        newView();

        DatePicker.DatePickerI18n i18n = invoke(view, "datePickerI18n", new Class<?>[0]);

        assertEquals(12, i18n.getMonthNames().size());
        assertEquals("January", i18n.getMonthNames().get(0));
        assertEquals("December", i18n.getMonthNames().get(11));
        assertEquals(List.of("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"), i18n.getWeekdays());
        assertEquals(List.of("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"), i18n.getWeekdaysShort());
        assertEquals(t("registerView.datePicker.today"), i18n.getToday());
        assertEquals(t("common.cancel"), i18n.getCancel());
    }

    @Test
    void given_theUploadTexts_when_built_then_everyLabelAndErrorIsTranslated() {
        newView();

        UploadI18N i18n = invoke(view, "uploadI18n", new Class<?>[0]);

        assertEquals(t("registerView.btnUploadCsv"), i18n.getAddFiles().getOne());
        assertEquals(t("registerView.btnUploadCsv"), i18n.getAddFiles().getMany());
        assertEquals(t("registerView.upload.dropFile"), i18n.getDropFiles().getOne());
        assertEquals(t("registerView.upload.dropFile"), i18n.getDropFiles().getMany());
        assertEquals(t("registerView.upload.error.tooBig"), i18n.getError().getFileIsTooBig());
        assertEquals(t("registerView.upload.error.wrongType"), i18n.getError().getIncorrectFileType());
        assertEquals(t("registerView.upload.error.tooMany"), i18n.getError().getTooManyFiles());
    }

    @Test
    void given_theInstructions_when_built_then_theyIntroduceTheMethodsAndDescribeEveryCsvColumn() {
        newView();

        Div instructions = invoke(view, "getRestfulInstructionsDiv", new Class<?>[0]);

        List<Component> parts = instructions.getChildren().toList();
        assertEquals(3, parts.size());
        assertEquals(t("registerView.intro.methods"), assertInstanceOf(Paragraph.class, parts.get(0)).getText());
        assertEquals(t("registerView.intro.clickBelow"), assertInstanceOf(Paragraph.class, parts.get(1)).getText());
        Details csv = assertInstanceOf(Details.class, parts.get(2));
        assertFalse(csv.isOpened());
        assertEquals(t("registerView.csvDoc.title"), csv.getSummaryText());

        List<ListItem> columns = find(ListItem.class, csv).all();
        List<String> names = List.of("departmentId", "firstName", "lastName", "middleName", "dob", "email", "phone", "xid");
        assertEquals(names.size(), columns.size());
        for (int i = 0; i < names.size(); i++) {
            List<Span> spans = find(Span.class, columns.get(i)).all();
            assertEquals(names.get(i) + ":", spans.get(0).getText());
            assertTrue(spans.get(0).hasClassName("inline-name"));
            assertEquals(" " + t("registerView.csvDoc.col." + names.get(i)), spans.get(1).getText());
        }
        assertTrue(find(Pre.class, csv).single().getText().startsWith("#departmentId,firstName,lastName"));
    }

    @Test
    void given_theRestInstructions_when_built_then_everyEndpointIsDocumented() {
        newView();

        Div content = invoke(view, "createRestApiContent", new Class<?>[0]);

        List<String> pre = find(Pre.class, content).all().stream().map(Pre::getText).toList();
        assertTrue(pre.containsAll(List.of("POST /api/secured/add/subject", "POST /api/secured/add/subjects",
                "POST /api/secured/add/csv", "multipart/form-data")), pre.toString());
        assertEquals(List.of(t("registerView.apiDoc.single.h"), t("registerView.apiDoc.bulk.h"), t("registerView.apiDoc.csvUpload.h"),
                t("registerView.apiDoc.webUpload.h"), t("registerView.apiDoc.response.h")),
                find(H3.class, content).all().stream().map(H3::getText).toList());
    }
}
