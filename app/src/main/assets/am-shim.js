// Auto Mes OQC (APK) - giả lập window.chrome.webview của WebView2 để index.html chạy y như trong AutoMesOQC.exe
(function () {
  'use strict';
  window.__AUTOMES_VIEWER = { folder: __FOLDER__, machineName: __MACHINE__ };
  var listeners = [];
  window.chrome = window.chrome || {};
  window.chrome.webview = {
    postMessage: function (s) { AndroidBridge.post(String(s)); },
    addEventListener: function (t, fn) { if (t === 'message' && typeof fn === 'function') listeners.push(fn); },
    removeEventListener: function (t, fn) { listeners = listeners.filter(function (x) { return x !== fn; }); }
  };
  // Java trả lời bằng cách gọi hàm này
  window.__amRecv = function (s) {
    listeners.slice().forEach(function (fn) { try { fn({ data: s }); } catch (e) { /* bỏ qua */ } });
  };
  // WebView không tự tải được link blob: -> đưa file sang Java để người dùng chọn nơi lưu
  document.addEventListener('click', function (e) {
    var a = e.target && e.target.closest ? e.target.closest('a[download]') : null;
    if (!a) return;
    var href = a.href || '';
    if (href.indexOf('blob:') !== 0) return;
    e.preventDefault(); e.stopPropagation();
    var name = a.download || 'file';
    fetch(href).then(function (r) { return r.blob(); }).then(function (b) {
      var rd = new FileReader();
      rd.onload = function () {
        var s = String(rd.result);
        AndroidBridge.saveFile(name, b.type || 'application/octet-stream', s.slice(s.indexOf(',') + 1));
      };
      rd.readAsDataURL(b);
    });
  }, true);
})();
