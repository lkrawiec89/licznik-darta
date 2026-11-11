// LICZNIK DARTA – Landing Page Scripts
document.addEventListener('DOMContentLoaded', () => {
  const GITHUB_REPO = 'lkrawiec89/licznik-darta';
  const FALLBACK_APK = 'https://github.com/lkrawiec89/licznik-darta/releases/latest/download/LicznikDarta-1.1.24.apk';
  const FALLBACK_VERSION = 'v1.1.24';

  // 1. Dynamic GitHub Release Fetcher
  async function fetchLatestRelease() {
    try {
      const res = await fetch(`https://api.github.com/repos/${GITHUB_REPO}/releases/latest`);
      if (!res.ok) return;
      const data = await res.json();
      
      const tagName = data.tag_name || FALLBACK_VERSION;
      const heroVersion = document.getElementById('hero-version');
      if (heroVersion) heroVersion.textContent = tagName;

      // Find apk asset if available
      const apkAsset = data.assets && data.assets.find(a => a.name.endsWith('.apk'));
      if (apkAsset && apkAsset.browser_download_url) {
        const downloadBtn = document.getElementById('primary-download-btn');
        if (downloadBtn) {
          downloadBtn.href = apkAsset.browser_download_url;
        }
      }
    } catch (e) {
      // Graceful fallback to static URLs
      console.log('Using static release details');
    }
  }
  fetchLatestRelease();

  // 2. Interactive Phone Screen Switcher
  const screenImg = document.getElementById('active-screen-img');
  const screenTabs = document.querySelectorAll('.screen-tab');

  screenTabs.forEach(tab => {
    tab.addEventListener('click', () => {
      const targetImg = tab.getAttribute('data-img');
      if (!targetImg || !screenImg) return;

      screenTabs.forEach(t => t.classList.remove('active'));
      tab.classList.add('active');

      screenImg.style.opacity = '0';
      setTimeout(() => {
        screenImg.src = targetImg;
        screenImg.style.opacity = '1';
      }, 150);
    });
  });

  // 3. One-Click Copy Link with Visual Feedback
  const copyBtn = document.getElementById('copy-link-btn');
  const copyText = document.getElementById('copy-btn-text');

  if (copyBtn) {
    copyBtn.addEventListener('click', async () => {
      const currentUrl = window.location.href.split('#')[0];
      try {
        await navigator.clipboard.writeText(currentUrl);
        const originalText = copyText ? copyText.textContent : '';
        if (copyText) copyText.textContent = '✓ Skopiowano link!';
        copyBtn.style.borderColor = '#00e676';
        copyBtn.style.color = '#00e676';

        setTimeout(() => {
          if (copyText) copyText.textContent = originalText;
          copyBtn.style.borderColor = '';
          copyBtn.style.color = '';
        }, 2500);
      } catch (err) {
        // Fallback for older browsers
        window.prompt('Skopiuj link do strony:', currentUrl);
      }
    });
  }
});
