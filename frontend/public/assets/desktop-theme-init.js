// Apply only the theme preference before Angular renders.
(function () {
  var choice = 'system';
  try {
    var stored = localStorage.getItem('fastcoloc.desktop-theme.v1');
    if (stored === 'light' || stored === 'dark') choice = stored;
  } catch (e) {}
  {
    document.documentElement.setAttribute(
      'data-desktop-theme',
      choice === 'system'
        ? window.matchMedia('(prefers-color-scheme: dark)').matches
          ? 'dark'
          : 'light'
        : choice,
    );
  }
})();
