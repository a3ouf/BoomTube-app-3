(function () {
  if (window.__vasCredInjected) return;
  var B = window.VASApp;
  if (!B) return;
  window.__vasCredInjected = true;

  var pending = null, pendingAt = 0, filled = false;

  function vis(e) {
    return !!e && (e.offsetParent !== null || e.getClientRects().length > 0);
  }

  function pwField() {
    var l = document.querySelectorAll('input[type=password]');
    for (var i = 0; i < l.length; i++) if (vis(l[i])) return l[i];
    return null;
  }

  function userField(pw) {
    var all = Array.prototype.slice.call(document.querySelectorAll('input'));
    var idx = all.indexOf(pw);
    for (var i = idx - 1; i >= 0; i--) {
      var t = (all[i].type || 'text').toLowerCase();
      if ((t === 'text' || t === 'email' || t === 'tel' || t === 'number') && vis(all[i])) return all[i];
    }
    return null;
  }

  function setVal(el, v) {
    var d = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value');
    d.set.call(el, v);
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }

  function capture() {
    var pw = pwField();
    if (!pw || !pw.value) return;
    var u = userField(pw);
    pending = { u: u ? u.value : '', p: pw.value };
    pendingAt = Date.now();
  }

  function submit() {
    var pw = pwField();
    if (!pw) return;
    var n = pw;
    for (var i = 0; i < 6 && n; i++) {
      n = n.parentElement;
      if (!n) break;
      var bs = n.querySelectorAll('button,input[type=submit]');
      if (bs.length) {
        var pick = null, j;
        for (j = 0; j < bs.length; j++) {
          if (vis(bs[j]) && /sign|log|enter|دخول/i.test(bs[j].innerText || bs[j].value || '')) { pick = bs[j]; break; }
        }
        if (!pick) for (j = 0; j < bs.length; j++) { if (vis(bs[j])) { pick = bs[j]; break; } }
        if (pick) { pick.click(); return; }
      }
    }
    if (pw.form && pw.form.requestSubmit) pw.form.requestSubmit();
  }

  document.addEventListener('click', function (e) {
    if (e.target && e.target.closest && e.target.closest('button,input[type=submit],[role=button],a')) capture();
  }, true);
  document.addEventListener('keydown', function (e) { if (e.key === 'Enter') capture(); }, true);
  document.addEventListener('submit', capture, true);

  setInterval(function () {
    var pw = pwField();

    // Commit credentials only after the login screen has gone away (= login succeeded)
    if (pending) {
      if (!pw) { B.saveCreds(pending.u, pending.p); pending = null; }
      else if (Date.now() - pendingAt > 6000) { pending = null; } // still on login screen -> wrong password, discard
    }

    // Auto-fill saved credentials
    if (pw && !pw.value && !filled) {
      var c = B.getCreds();
      if (c) {
        try {
          var o = JSON.parse(c);
          var u = userField(pw);
          if (u && o.u) setVal(u, o.u);
          setVal(pw, o.p);
          filled = true;
          if (B.consumeAutoLogin()) setTimeout(submit, 500);
        } catch (err) {}
      }
    }
    if (!pw) filled = false;
  }, 700);
})();
