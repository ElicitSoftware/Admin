package com.elicitsoftware.admin.i18n;

/*-
 * ***LICENSE_START***
 * Elicit Admin
 * %%
 * Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.vaadin.flow.component.UI;
import com.vaadin.flow.i18n.I18NProvider;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinSession;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;

import java.util.Locale;

/**
 * Translation lookup for code that is not a Vaadin component (validators, the PDF renderer, REST
 * resources). Uses the running service's provider when there is one; otherwise the application's
 * own {@link ElicitI18NProvider} bean, so a REST resource or a background thread reads the same
 * translations mount as the UI; and outside a running application (plain unit tests) a standalone
 * provider configured from the same settings.
 */
public final class Translations {

    private static volatile ElicitI18NProvider standalone;

    private Translations() {
    }

    /**
     * Translates for the current UI's locale (or the session's, or English).
     *
     * @param key    the translation key
     * @param params the {@link java.text.MessageFormat} arguments, if the translation takes any
     * @return the translation; see {@link #get(Locale, String, Object...)}
     */
    public static String get(String key, Object... params) {
        return get(currentLocale(), key, params);
    }

    /**
     * Translates for the given locale.
     *
     * @param locale the locale to translate for; null means English
     * @param key    the translation key
     * @param params the {@link java.text.MessageFormat} arguments, if the translation takes any
     * @return the translation as the provider returns it (Elicit's provider falls back to English,
     *         and returns {@code !key!} for a key no bundle has)
     */
    public static String get(Locale locale, String key, Object... params) {
        return provider().getTranslation(key, locale == null ? ElicitI18NProvider.DEFAULT_LOCALE : locale, params);
    }

    /**
     * The locale of the current UI, else of the current Vaadin session, else English.
     *
     * @return the locale translations are made for on this thread
     */
    public static Locale currentLocale() {
        UI ui = UI.getCurrent();
        if (ui != null) {
            return ui.getLocale();
        }
        VaadinSession session = VaadinSession.getCurrent();
        if (session != null && session.getLocale() != null) {
            return session.getLocale();
        }
        return ElicitI18NProvider.DEFAULT_LOCALE;
    }

    static I18NProvider provider() {
        VaadinService service = VaadinService.getCurrent();
        if (service != null) {
            I18NProvider fromService = service.getInstantiator().getI18NProvider();
            if (fromService != null) {
                return fromService;
            }
        }
        ArcContainer container = Arc.container();
        if (container != null && container.isRunning()) {
            ElicitI18NProvider bean = container.instance(ElicitI18NProvider.class).get();
            if (bean != null) {
                return bean;
            }
        }
        ElicitI18NProvider p = standalone;
        if (p == null) {
            p = ElicitI18NProvider.fromConfig();
            standalone = p;
        }
        return p;
    }
}
