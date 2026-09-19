// Embeds the Survey Operations dashboard from Apache Superset into the Admin Analytics view
// (UC-020). The guest token is fetched from Admin's own endpoint with the console session
// cookie, and the SDK calls fetchGuestToken again before a token expires (UC-020 A3).
import { embedDashboard } from '@superset-ui/embedded-sdk';

window.elicitAnalytics = window.elicitAnalytics || {};

window.elicitAnalytics.embed = async function (mount, config) {
  const status = mount.querySelector('[data-role="status"]') || mount;
  const fetchGuestToken = async () => {
    const response = await fetch(config.tokenUrl, {
      credentials: 'same-origin',
      headers: { Accept: 'application/json' },
    });
    if (!response.ok) {
      throw new Error('guest token request failed with HTTP ' + response.status);
    }
    const body = await response.json();
    return body.token;
  };
  try {
    const dashboard = await embedDashboard({
      id: config.dashboardId,
      supersetDomain: config.supersetUrl,
      mountPoint: mount,
      fetchGuestToken,
      dashboardUiConfig: {
        hideTitle: true,
        hideChartControls: false,
        filters: { visible: true, expanded: false },
      },
      iframeAllowExtras: ['clipboard-write', 'fullscreen'],
    });
    const iframe = mount.querySelector('iframe');
    if (iframe) {
      iframe.style.width = '100%';
      iframe.style.height = '100%';
      iframe.style.border = '0';
      iframe.title = 'Survey Operations dashboard';
    }
    mount.dataset.embedded = 'true';
    return dashboard;
  } catch (error) {
    mount.dataset.embedded = 'error';
    mount.dispatchEvent(new CustomEvent('elicit-analytics-error', {
      detail: { message: error instanceof Error ? error.message : String(error) },
      bubbles: true,
    }));
    throw error;
  }
};
