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
import com.elicitsoftware.model.User;
import com.elicitsoftware.test.PostgresTestResource;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.flow.server.VaadinSession;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.enterprise.inject.spi.CDI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Browserless UI test for {@link SearchView}'s grid wiring.
 *
 * <p>Traceability: UC-002 (Search and Monitor Subject Progress). The parameterized query and
 * database-level paging that back this view (code-review findings #1 and #4) are covered by
 * {@code StatusDataSourceTest}. This test guards the <em>view-level</em> contract that finding
 * #12 addressed: every sortable grid column must declare a sort property equal to one of the
 * {@code Status.PROP_*} constants that {@code SearchView.buildSort(...)} maps against — so the
 * grid's sort properties and the query's sort mapping cannot drift apart.</p>
 *
 * <p>{@code SearchView} injects {@code UiSessionLogin} and reads the authenticated user from the
 * Vaadin session, so the test seeds a {@link User} (with one department) into the session and
 * obtains the view through CDI, then attaches it to the test {@link UI}. Route navigation is
 * unavailable under {@code @QuarkusTest} (see the project's browserless-testing notes).</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class SearchViewTest extends QuarkusBrowserlessTest {

    private SearchView view;

    @BeforeEach
    @TestSecurity(user = "search.tester", roles = {"elicit_user"})
    void setUp() {
        // SearchView reads the current user from the Vaadin session (populated at login by
        // UiSessionLogin). Seed a transient user with one department so the view initializes.
        User user = new User();
        user.setId(1);
        user.setUsername("search.tester");
        user.setActive(true);
        Department department = new Department();
        department.id = 1;
        department.name = "Search Dept";
        Set<Department> departments = new HashSet<>();
        departments.add(department);
        user.setDepartments(departments);
        VaadinSession.getCurrent().setAttribute("user", user);

        view = CDI.current().select(SearchView.class).get();
        UI.getCurrent().add(view);
    }

    @SuppressWarnings("unchecked")
    private Grid<Status> grid() {
        return find(Grid.class, view).single();
    }

    /** UC-002: the subject grid is rendered with the full set of columns. */
    @Test
    @TestSecurity(user = "search.tester", roles = {"elicit_user"})
    void gridRendersWithColumns() {
        // 9 data columns + Edit + Action component columns.
        assertTrue(grid().getColumns().size() >= 9,
                "the subject grid should render its data columns");
    }

    /**
     * UC-002 (#12): every sortable column's sort property is one of the {@code Status.PROP_*}
     * constants, so the grid sort keys stay in lock-step with the query sort mapping.
     */
    @Test
    @TestSecurity(user = "search.tester", roles = {"elicit_user"})
    void sortableColumnsUseEntityPropertyConstants() {
        Set<String> allowed = Set.of(
                Status.PROP_TOKEN, Status.PROP_DEPARTMENT_NAME, Status.PROP_FIRST_NAME,
                Status.PROP_MIDDLE_NAME, Status.PROP_LAST_NAME, Status.PROP_EMAIL,
                Status.PROP_PHONE, Status.PROP_STATUS, Status.PROP_CREATED_DT);

        List<String> sortProperties = grid().getColumns().stream()
                .filter(Grid.Column::isSortable)
                .flatMap(c -> c.getSortOrder(SortDirection.ASCENDING))
                .map(order -> order.getSorted())
                .collect(Collectors.toList());

        assertTrue(sortProperties.size() >= 9,
                "each sortable data column should expose a sort property");
        for (String property : sortProperties) {
            assertTrue(allowed.contains(property),
                    "sort property '" + property + "' must be a Status.PROP_* constant");
        }
    }

    /** UC-002 (#12): the token column specifically maps to the PROP_TOKEN constant. */
    @Test
    @TestSecurity(user = "search.tester", roles = {"elicit_user"})
    void tokenColumnMapsToTokenProperty() {
        long tokenMappings = grid().getColumns().stream()
                .filter(Grid.Column::isSortable)
                .flatMap(c -> c.getSortOrder(SortDirection.ASCENDING))
                .map(order -> order.getSorted())
                .filter(Status.PROP_TOKEN::equals)
                .count();
        assertEquals(1, tokenMappings, "exactly one column should sort by the token property");
    }
}
