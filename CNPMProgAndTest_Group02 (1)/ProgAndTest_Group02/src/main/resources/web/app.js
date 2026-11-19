'use strict';
/* Library Management System - web UI (view + client side controller).
   All data is rendered with textContent (never innerHTML) so user input cannot inject markup. */

// ------------------------------------------------------------------ helpers
// DOM append / replaceChildren would print "null" or "[object ...]" for empty values and arrays: ignore empty values, flatten arrays.
for (const method of ['append', 'replaceChildren']) {
  const original = Element.prototype[method];
  Element.prototype[method] = function (...kids) {
    return original.apply(this, kids.flat(Infinity).filter(k => k !== null && k !== undefined && k !== false));
  };
}

function h(tag, attrs, ...kids) {
  const e = document.createElement(tag);
  let value;
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v === null || v === undefined || v === false) continue;
    if (k === 'class') e.className = v;
    else if (k.startsWith('on')) e.addEventListener(k.slice(2), v);
    else if (k === 'value') value = v;
    else if (k === 'checked' || k === 'disabled' || k === 'selected') e[k] = v;
    else e.setAttribute(k, v === true ? '' : v);
  }
  for (const kid of kids.flat(Infinity)) {
    if (kid === null || kid === undefined || kid === false) continue;
    e.append(kid.nodeType ? kid : document.createTextNode(String(kid)));
  }
  if (value !== undefined) e.value = value;
  return e;
}

const $app = () => document.getElementById('app');
const money = n => new Intl.NumberFormat('vi-VN').format(Math.round(n || 0)) + ' VND';
const fmtDate = s => { if (!s) return '—'; const [y, m, d] = String(s).slice(0, 10).split('-'); return `${d}/${m}/${y}`; };
const fmtTime = s => s ? `${fmtDate(s)} ${String(s).slice(11, 16)}` : '—';
const badge = s => h('span', { class: 'badge ' + s }, s);
const todayIso = () => new Date().toISOString().slice(0, 10);

let toastTimer;
function toast(msg, isError) {
  const t = document.getElementById('toast');
  t.textContent = msg;
  t.className = 'toast show' + (isError ? ' error' : '');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { t.className = 'toast'; }, 4500);
}

// ------------------------------------------------------------------ session + api
const session = {
  token: sessionStorage.getItem('lms.token') || null,
  user: null,
  set(token, user) { this.token = token; this.user = user; token ? sessionStorage.setItem('lms.token', token) : sessionStorage.removeItem('lms.token'); },
  get role() { return this.user ? this.user.role : 'GUEST'; },
  isStaff() { return this.role === 'LIBRARIAN' || this.role === 'ADMIN'; }
};

async function api(method, path, body, raw) {
  const opts = { method, headers: {} };
  if (session.token) opts.headers['X-Auth-Token'] = session.token;
  if (body !== undefined) { opts.headers['Content-Type'] = 'application/json'; opts.body = JSON.stringify(body); }
  let res;
  try { res = await fetch(path, opts); }
  catch (e) { throw new Error('Cannot reach the server. Please try again.'); }
  if (raw && res.ok) return res;
  let data = {};
  try { data = await res.json(); } catch (e) { /* no body */ }
  if (!res.ok) {
    if (res.status === 401 && session.token) {
      session.set(null, null);
      renderNav();
      go('#/login');
      toast(data.error || 'Please log in again', true);
    }
    const err = new Error(data.error || 'Request failed (' + res.status + ')');
    err.field = data.field;
    err.status = res.status;
    throw err;
  }
  return data;
}

// ------------------------------------------------------------------ forms
function field(label, name, o = {}) {
  let input;
  if (o.options) {
    input = h('select', { name, disabled: o.readonly }, o.options.map(x => {
      const [val, text] = Array.isArray(x) ? x : [x, x];
      return h('option', { value: val }, text);
    }));
    input.value = o.value !== undefined ? o.value : '';
  } else if (o.area) {
    input = h('textarea', { name, rows: 3, placeholder: o.placeholder }, o.value || '');
  } else {
    input = h('input', { name, type: o.type || 'text', value: o.value === undefined || o.value === null ? '' : o.value,
      placeholder: o.placeholder, readonly: o.readonly, list: o.list, autocomplete: o.autocomplete || 'off', min: o.min, step: o.step });
  }
  return h('div', { class: 'field' }, h('label', { for: 'f-' + name }, label), (input.id = 'f-' + name, input));
}

function values(form) {
  const out = {};
  for (const el of form.querySelectorAll('[name]')) out[el.name] = el.value;
  return out;
}

function showError(form, err) {
  form.querySelectorAll('.invalid').forEach(e => e.classList.remove('invalid'));
  const box = form.querySelector('.form-error');
  if (box) box.textContent = err.message;
  if (err.field) {
    const el = form.querySelector('[name="' + err.field + '"]');
    if (el) { el.classList.add('invalid'); el.focus(); }
  }
}

function clearError(form) {
  form.querySelectorAll('.invalid').forEach(e => e.classList.remove('invalid'));
  const box = form.querySelector('.form-error');
  if (box) box.textContent = '';
}

/** Runs an async action with error handling shown inside a form (or as a toast). */
async function run(form, fn) {
  if (form) clearError(form);
  try { return await fn(); }
  catch (e) { if (form) showError(form, e); else toast(e.message, true); return undefined; }
}

function table(columns, rows, emptyText) {
  if (!rows.length) return h('div', { class: 'table-wrap' }, h('div', { class: 'empty' }, emptyText || 'No data'));
  return h('div', { class: 'table-wrap' }, h('table', {},
    h('thead', {}, h('tr', {}, columns.map(c => h('th', {}, c)))),
    h('tbody', {}, rows.map(r => h('tr', {}, r.map(cell => h('td', { class: cell && cell.isActions ? 'actions-cell' : '' }, cell && cell.isActions ? cell.items : cell)))))));
}

const actions = (...items) => ({ isActions: true, items });

function pager(page, totalPages, onPage) {
  return h('div', { class: 'pager' },
    h('button', { class: 'secondary small', disabled: page <= 1, onclick: () => onPage(page - 1) }, '‹ Previous'),
    h('span', {}, `Page ${page} of ${totalPages}`),
    h('button', { class: 'secondary small', disabled: page >= totalPages, onclick: () => onPage(page + 1) }, 'Next ›'));
}

// ------------------------------------------------------------------ navigation
function go(hash) { if (location.hash === hash) route(); else location.hash = hash; }

function renderNav() {
  const nav = document.getElementById('nav');
  nav.replaceChildren();
  const cur = location.hash || '#/search';
  const link = (hash, text) => h('a', { href: hash, class: cur.startsWith(hash) ? 'active' : '' }, text);
  nav.append(link('#/search', 'Search books'));
  if (session.role === 'GUEST') nav.append(link('#/login', 'Log in'), link('#/register', 'Register'));
  if (session.role === 'MEMBER') nav.append(link('#/loans', 'My loans'), link('#/profile', 'My profile'));
  if (session.isStaff()) nav.append(link('#/lend', 'Lend / Return'), link('#/books', 'Manage books'), link('#/members', 'Manage members'));
  if (session.role === 'ADMIN') nav.append(link('#/staff', 'Manage staff'), link('#/reports', 'Reports'));
  if (session.user) {
    nav.append(h('span', { class: 'who' }, `${session.user.fullName} (${session.role})`),
      h('button', { class: 'link', id: 'logout', onclick: logout }, 'Log out'));
  }
}

async function logout() {
  try { await api('POST', '/api/logout'); } catch (e) { /* ignore */ }
  session.set(null, null);
  renderNav();
  go('#/login');
  toast('You have been logged out');
}

const pages = {};

async function route() {
  renderNav();
  const [, name, ...args] = (location.hash || '#/search').split('/');
  const page = pages[name] || pages.search;
  const need = page.needs;
  const app = $app();
  app.replaceChildren();
  if (need && !need(session)) {
    app.append(h('div', { class: 'card info-bad' }, session.user
      ? 'Access denied: your role is not allowed to open this page.'
      : 'Please log in to open this page.'));
    return;
  }
  try { await page.render(app, ...args); }
  catch (e) { app.replaceChildren(h('div', { class: 'card info-bad' }, e.message)); }
}

const needs = {
  member: s => s.role === 'MEMBER',
  staff: s => s.isStaff(),
  admin: s => s.role === 'ADMIN'
};

// =================================================================== PUBLIC PAGES
pages.search = {
  async render(app) {
    const cats = await api('GET', '/api/categories');
    const state = { q: '', type: 'ALL', category: '0', sort: '', page: 1 };
    const form = h('form', { class: 'card' },
      h('div', { class: 'row' },
        field('Search', 'q', { placeholder: 'Enter title, author, category or ISBN…' }),
        field('Search by', 'type', { options: [['ALL', 'All'], ['TITLE', 'Title'], ['AUTHOR', 'Author'], ['CATEGORY', 'Category'], ['ISBN', 'ISBN']], value: 'ALL' }),
        field('Category', 'category', { options: [['0', 'All'], ...cats.map(c => [String(c.id), c.name])], value: '0' }),
        field('Sort by', 'sort', { options: [['', 'Default'], ['title', 'Title'], ['newest', 'Newest']], value: '' }),
        h('div', { class: 'fixed' }, h('button', { type: 'submit', id: 'btn-search' }, 'Search'))),
      h('p', { class: 'form-error' }));
    const result = h('div', { id: 'results' });
    async function load() {
      const q = new URLSearchParams({ q: state.q, type: state.type, category: state.category, sort: state.sort, page: state.page });
      const res = await run(null, () => api('GET', '/api/books?' + q));
      if (!res) return;
      const rows = res.items.map(b => [
        h('a', { href: '#/book/' + b.bookId }, b.title), b.authors.join(', '), b.category,
        h('span', { class: b.availableCopies > 0 ? '' : 'muted' }, String(b.availableCopies)),
        h('a', { class: 'btn small', href: '#/book/' + b.bookId }, 'Details')]);
      result.replaceChildren(
        h('p', { class: 'muted' }, `${res.total} book(s) found`),
        table(['Title', 'Author', 'Category', 'Available', ''], rows, 'No books found'),
        res.total > 0 ? pager(res.page, res.totalPages, p => { state.page = p; load(); }) : null);
    }
    form.addEventListener('submit', e => {
      e.preventDefault();
      Object.assign(state, values(form), { page: 1 });
      load();
    });
    app.append(h('h1', {}, 'Search books'), form, result);
    load();
  }
};

pages.book = {
  async render(app, id) {
    const b = await api('GET', '/api/books/' + id);
    const info = (k, v) => h('p', {}, h('strong', {}, k + ': '), v === null || v === undefined || v === '' ? '—' : v);
    const msg = h('p', { class: 'form-error', id: 'book-msg' });
    let action = null;
    if (b.availableCopies === 0) {
      if (session.role === 'MEMBER') {
        action = h('button', { id: 'btn-reserve', onclick: async () => {
          const r = await run(null, () => api('POST', '/api/reservations', { bookId: b.bookId }));
          if (r) { toast(r.message); msg.textContent = r.message; }
        } }, 'Reserve this book');
      } else if (session.role === 'GUEST') {
        action = h('p', { class: 'muted' }, 'All copies are on loan. Log in as a member to reserve this book.');
      }
    }
    app.append(h('h1', {}, 'Book details'), h('div', { class: 'card detail' },
      h('div', { class: 'cover' }, 'Book cover'),
      h('div', {}, h('h2', { style: 'margin-top:0' }, b.title),
        info('Author', b.authors.join(', ')), info('Category', b.category), info('Publisher', b.publisher),
        info('ISBN', b.isbn), info('Year', b.publishYear),
        info('Available copies', `${b.availableCopies}` + (b.shelfLocation ? ` (${b.shelfLocation})` : '')),
        info('Description', b.description), action, msg)),
      h('a', { href: '#/search' }, '‹ Back to search'));
  }
};

pages.login = {
  async render(app) {
    const form = h('form', { class: 'card narrow', id: 'login-form' }, h('h1', { style: 'text-align:center' }, 'Login'),
      field('Username', 'username', { autocomplete: 'username' }), field('Password', 'password', { type: 'password', autocomplete: 'current-password' }),
      h('p', { class: 'form-error' }),
      h('div', { class: 'actions' }, h('button', { type: 'submit', id: 'btn-login' }, 'Log in'), h('button', { type: 'button', class: 'secondary', onclick: () => history.back() }, 'Cancel')),
      h('p', { class: 'row' }, h('a', { href: '#/forgot' }, 'Forgot password?'), h('a', { href: '#/register', style: 'text-align:right' }, 'Register')));
    form.addEventListener('submit', async e => {
      e.preventDefault();
      const r = await run(form, () => api('POST', '/api/login', values(form)));
      if (!r) return;
      session.set(r.token, r.user);
      toast('Welcome, ' + r.user.fullName);
      go(session.isStaff() ? '#/lend' : '#/search');
    });
    app.append(form);
  }
};

pages.forgot = {
  async render(app) {
    const form = h('form', { class: 'card narrow' }, h('h1', { style: 'text-align:center' }, 'Reset password'),
      h('p', { class: 'muted' }, 'Enter the email of your account. A new temporary password will be sent to it.'),
      field('Email', 'email', { type: 'email' }), h('p', { class: 'form-error' }),
      h('div', { class: 'actions' }, h('button', { type: 'submit', id: 'btn-reset' }, 'Send new password'), h('a', { class: 'btn secondary', href: '#/login' }, 'Back to login')));
    form.addEventListener('submit', async e => {
      e.preventDefault();
      const r = await run(form, () => api('POST', '/api/password-reset', values(form)));
      if (r) { toast(r.message); form.querySelector('.form-error').textContent = ''; form.append(h('div', { class: 'info-ok' }, r.message)); }
    });
    app.append(form);
  }
};

pages.register = {
  async render(app) {
    const form = h('form', { class: 'card narrow', id: 'register-form', style: 'background:#fff;max-width:560px' }, h('h1', {}, 'Register account'));
    const captchaBox = h('div', {});
    let captchaId = '';
    async function loadCaptcha() {
      const c = await api('GET', '/api/captcha');
      captchaId = c.id;
      captchaBox.replaceChildren(field('CAPTCHA: ' + c.question, 'captchaAnswer'));
    }
    form.append(field('Full name', 'fullName'), field('Date of birth', 'dateOfBirth', { type: 'date' }), field('Address', 'address'),
      field('Phone', 'phone', { type: 'tel' }), field('Email', 'email', { type: 'email' }), field('Username', 'username'),
      field('Password (at least 6 characters)', 'password', { type: 'password', autocomplete: 'new-password' }),
      field('Confirm password', 'confirmPassword', { type: 'password', autocomplete: 'new-password' }), captchaBox,
      h('p', { class: 'form-error' }),
      h('div', { class: 'actions' }, h('button', { type: 'submit', id: 'btn-register' }, 'Submit'), h('a', { class: 'btn secondary', href: '#/login' }, 'Cancel')));
    form.addEventListener('submit', async e => {
      e.preventDefault();
      const r = await run(form, () => api('POST', '/api/register', { ...values(form), captchaId }));
      if (r) { app.replaceChildren(h('div', { class: 'card info-ok' }, h('h2', {}, r.message), h('a', { href: '#/login' }, 'Go to login'))); return; }
      loadCaptcha();
    });
    app.append(form);
    await loadCaptcha();
  }
};

// =================================================================== MEMBER PAGES
pages.loans = {
  needs: needs.member,
  async render(app) {
    const filter = field('Status', 'status', { options: [['ALL', 'All'], ['BORROWED', 'Borrowed'], ['OVERDUE', 'Overdue'], ['RETURNED', 'Returned'], ['LOST', 'Lost']], value: 'ALL' });
    const box = h('div', {}), resBox = h('div', {});
    async function load() {
      const st = filter.querySelector('select').value;
      const d = await api('GET', '/api/loans/mine?status=' + st);
      box.replaceChildren(table(['Book', 'Barcode', 'Loan date', 'Due date', 'Returned', 'Status', 'Renewals', 'Fine', ''],
        d.loans.map(l => [l.title, l.barcode, fmtDate(l.loanDate), fmtDate(l.dueDate), fmtDate(l.returnDate), badge(l.status),
          `${l.renewCount} / ${l.maxRenewals}`, l.fineAmount > 0 ? h('span', {}, money(l.fineAmount) + ' ', badge(l.fineStatus)) : '—',
          l.canRenew ? h('button', { class: 'small', onclick: async () => {
            const r = await run(null, () => api('POST', `/api/loans/${l.loanId}/renew`));
            if (r) { toast(r.message); load(); }
          } }, 'Renew') : '—']), 'No loans yet'),
        h('p', {}, h('strong', {}, 'Unpaid fines: '), money(d.unpaidFines)));
    }
    async function loadRes() {
      const rs = await api('GET', '/api/reservations');
      resBox.replaceChildren(table(['Book', 'Reserved at', 'Status', 'Queue position / pick up before', ''],
        rs.map(r => [h('a', { href: '#/book/' + r.bookId }, r.title), fmtTime(r.reservedAt), badge(r.status),
          r.status === 'WAITING' ? '#' + r.position : r.status === 'READY' ? 'Pick up before ' + fmtTime(r.expiresAt) : '—',
          (r.status === 'WAITING' || r.status === 'READY') ? h('button', { class: 'small secondary', onclick: async () => {
            if (!confirm('Cancel this reservation?')) return;
            if (await run(null, () => api('DELETE', '/api/reservations/' + r.reservationId)) !== undefined) { toast('Reservation cancelled'); loadRes(); }
          } }, 'Cancel') : '']), 'No reservations'));
    }
    filter.querySelector('select').addEventListener('change', load);
    app.append(h('h1', {}, 'My loans'), h('div', { style: 'max-width:240px' }, filter), box, h('h2', {}, 'My reservations'), resBox);
    await load();
    await loadRes();
  }
};

pages.profile = {
  needs: needs.member,
  async render(app) {
    const u = await api('GET', '/api/me');
    const form = h('form', { class: 'card', id: 'profile-form', style: 'max-width:640px' },
      h('p', { class: 'muted' }, `Username: ${u.username} · Status: ${u.status} · Card: ${u.cardNumber || 'not issued'}` + (u.cardExpiry ? ` (valid until ${fmtDate(u.cardExpiry)})` : '')),
      field('Username', 'usernameView', { value: u.username, readonly: true }),
      field('Full name', 'fullName', { value: u.fullName }), field('Date of birth', 'dateOfBirth', { type: 'date', value: u.dateOfBirth }),
      field('Address', 'address', { value: u.address }), field('Phone', 'phone', { value: u.phone }), field('Email', 'email', { type: 'email', value: u.email }),
      h('h2', {}, 'Change password (optional)'),
      field('Current password', 'currentPassword', { type: 'password', autocomplete: 'current-password' }),
      field('New password', 'newPassword', { type: 'password', autocomplete: 'new-password' }),
      h('p', { class: 'form-error' }),
      h('div', { class: 'actions' }, h('button', { type: 'submit', id: 'btn-save-profile' }, 'Save'), h('button', { type: 'button', class: 'secondary', onclick: route }, 'Cancel')));
    form.addEventListener('submit', async e => {
      e.preventDefault();
      const v = values(form);
      delete v.usernameView;
      const r = await run(form, () => api('PUT', '/api/profile', v));
      if (r) { session.user = await api('GET', '/api/me'); renderNav(); toast(r.message); form.querySelector('[name=currentPassword]').value = ''; form.querySelector('[name=newPassword]').value = ''; }
    });
    app.append(h('h1', {}, 'My profile'), form);
  }
};

// =================================================================== LIBRARIAN PAGES
function receiptView(r) {
  return h('div', { class: 'slip', id: 'receipt' }, h('h3', {}, 'Receipt'),
    h('p', {}, 'Member: ' + r.member + (r.cardNumber ? ` (${r.cardNumber})` : '')), h('p', {}, 'Book: ' + r.title),
    h('p', {}, `Reason: ${r.reason}`), h('p', {}, 'Amount: ' + money(r.amount)),
    h('p', {}, 'Paid at: ' + fmtTime(r.paidAt)), h('p', {}, 'Librarian: ' + r.librarian),
    h('div', { class: 'actions no-print' }, h('button', { class: 'secondary small', onclick: () => window.print() }, 'Print')));
}

function fineRows(fines, onPaid, receiptBox) {
  return table(['Book', 'Reason', 'Amount', 'Status', ''], fines.map(f => [f.title, f.reason, money(f.amount), badge(f.status),
    f.status === 'UNPAID' ? h('button', { class: 'small', onclick: async () => {
      const r = await run(null, () => api('POST', `/api/fines/${f.fineId}/pay`));
      if (r) { toast(r.message); receiptBox.replaceChildren(receiptView(r.receipt)); onPaid(f); }
    } }, 'Collect payment') : '—']), 'No fines');
}

pages.lend = {
  needs: needs.staff,
  async render(app) {
    const st = { member: null, items: [] };
    const infoBox = h('div', {}), itemsBox = h('div', {}), slipBox = h('div', { class: 'slip', id: 'slip' });
    const cardForm = h('form', {}, h('div', { class: 'row' }, field('Member card number', 'card', { placeholder: 'LIB-000123' }),
      h('div', { class: 'fixed' }, h('button', { type: 'submit', id: 'btn-check' }, 'Check'))), h('p', { class: 'form-error' }));
    const addForm = h('form', {}, h('div', { class: 'row' }, field('Book copy barcode', 'barcode', { placeholder: 'BC-000004' }),
      h('div', { class: 'fixed' }, h('button', { type: 'submit', id: 'btn-add' }, 'Add'))), h('p', { class: 'form-error' }));
    const confirmBtn = h('button', { id: 'btn-confirm', disabled: true, onclick: confirmLend }, 'Confirm Lend');
    const lendMsg = h('p', { class: 'form-error' });

    function renderSlip(done) {
      const today = todayIso(), due = new Date(Date.now() + 14 * 864e5).toISOString().slice(0, 10);
      slipBox.replaceChildren(h('h3', {}, done ? 'Loan Slip' : 'Loan Slip (Preview)'),
        h('p', {}, 'Member: ' + (st.member ? st.member.fullName : '—')),
        ...(done ? done.items : st.items).map(it => h('p', {}, 'Book: ' + it.title + (it.barcode ? ` [${it.barcode}]` : ''))),
        h('p', {}, 'Loan date: ' + fmtDate(done ? done.items[0].loanDate : today)), h('p', {}, 'Due date: ' + fmtDate(done ? done.items[0].dueDate : due)),
        h('p', {}, 'Librarian: ' + session.user.username),
        done ? h('div', { class: 'actions no-print' }, h('button', { class: 'secondary small', onclick: () => window.print() }, 'Print')) : null);
    }
    function renderItems() {
      itemsBox.replaceChildren(table(['Barcode', 'Title', 'Status', ''], st.items.map((it, i) => [it.barcode, it.title, badge(it.status),
        h('button', { class: 'small secondary', onclick: () => { st.items.splice(i, 1); renderItems(); } }, 'Remove')]), 'No copy added yet'));
      confirmBtn.disabled = !(st.member && st.member.eligible && st.items.length);
      renderSlip();
    }
    cardForm.addEventListener('submit', async e => {
      e.preventDefault();
      st.member = null;
      infoBox.replaceChildren();
      const m = await run(cardForm, () => api('GET', '/api/lending/member?card=' + encodeURIComponent(values(cardForm).card)));
      if (!m) { renderItems(); return; }
      st.member = m;
      infoBox.replaceChildren(h('div', { class: m.eligible ? 'info-ok' : 'info-bad', id: 'member-info' },
        h('div', {}, `${m.fullName} - ${m.status}`), h('div', {}, `Loans: ${m.activeLoans}/${m.maxLoans}   Unpaid fines: ${m.unpaidFines > 0 ? money(m.unpaidFines) : 0}`),
        m.eligible ? null : h('div', {}, h('strong', {}, 'Cannot lend: '), m.reason)));
      renderItems();
    });
    addForm.addEventListener('submit', async e => {
      e.preventDefault();
      const code = values(addForm).barcode.trim();
      if (!code) return;
      const c = await run(addForm, () => api('GET', '/api/copies/lookup?barcode=' + encodeURIComponent(code)));
      if (!c) return;
      if (st.items.some(x => x.barcode === c.barcode)) { showError(addForm, { message: 'This copy is already in the list', field: 'barcode' }); return; }
      if (c.status !== 'AVAILABLE' && c.status !== 'RESERVED') { showError(addForm, { message: `Copy ${c.barcode} is not available (status: ${c.status})`, field: 'barcode' }); return; }
      st.items.push(c);
      addForm.querySelector('[name=barcode]').value = '';
      renderItems();
    });
    async function confirmLend() {
      lendMsg.textContent = '';
      const r = await run(null, () => api('POST', '/api/lending/lend', { card: st.member.cardNumber, barcodes: st.items.map(i => i.barcode) }));
      if (!r) return;
      toast(r.message);
      renderSlip(r.slip);
      st.items = []; itemsBox.replaceChildren(table(['Barcode', 'Title', 'Status', ''], [], 'No copy added yet'));
      confirmBtn.disabled = true;
      const mm = await run(null, () => api('GET', '/api/lending/member?card=' + encodeURIComponent(st.member.cardNumber)));
      if (mm) { st.member = mm; infoBox.replaceChildren(h('div', { class: mm.eligible ? 'info-ok' : 'info-bad' }, `${mm.fullName} - ${mm.status}`, h('div', {}, `Loans: ${mm.activeLoans}/${mm.maxLoans}`))); }
    }

    // ---- return
    const retForm = h('form', { id: 'return-form' }, h('div', { class: 'row' },
      field('Book copy barcode', 'barcode', { placeholder: 'BC-000004' }),
      field('Condition', 'condition', { options: [['OK', 'Good condition'], ['DAMAGED', 'Damaged'], ['LOST', 'Lost']], value: 'OK' }),
      field('Replacement price (VND)', 'replacementPrice', { type: 'number', min: '1' }),
      h('div', { class: 'fixed' }, h('button', { type: 'submit', id: 'btn-return' }, 'Returned Book'))), h('p', { class: 'form-error' }));
    const priceField = retForm.querySelector('[name=replacementPrice]').parentElement;
    priceField.style.display = 'none';
    retForm.querySelector('[name=condition]').addEventListener('change', e => { priceField.style.display = e.target.value === 'OK' ? 'none' : ''; });
    const retBox = h('div', {}), retReceipt = h('div', {});
    retForm.addEventListener('submit', async e => {
      e.preventDefault();
      retBox.replaceChildren(); retReceipt.replaceChildren();
      const v = values(retForm);
      const r = await run(retForm, () => api('POST', '/api/lending/return', { barcode: v.barcode, condition: v.condition, replacementPrice: v.replacementPrice ? Number(v.replacementPrice) : null }));
      if (!r) return;
      toast(r.message);
      const head = h('div', { class: r.fines.length ? 'info-bad' : 'info-ok', id: 'return-result' },
        h('div', {}, `${r.title} [${r.barcode}] returned by ${r.member} on ${fmtDate(r.returnDate)}. Copy status: ${r.copyStatus}`),
        r.fines.length ? h('div', {}, h('strong', {}, 'Fine to collect:')) : h('div', {}, 'No fine.'));
      const fines = r.fines.map(f => ({ ...f }));
      const finTable = h('div', {});
      const draw = () => finTable.replaceChildren(fines.length ? fineRows(fines, f => { f.status = 'PAID'; draw(); }, retReceipt) : '');
      draw();
      retBox.replaceChildren(head, finTable);
      retForm.querySelector('[name=barcode]').value = '';
    });

    // ---- fines of a member
    const finForm = h('form', {}, h('div', { class: 'row' }, field('Member card number', 'card', { placeholder: 'LIB-000126' }),
      h('div', { class: 'fixed' }, h('button', { type: 'submit', id: 'btn-fines' }, 'Show fines'))), h('p', { class: 'form-error' }));
    const finBox = h('div', {}), finReceipt = h('div', {});
    async function loadFines() {
      const r = await run(finForm, () => api('GET', '/api/fines?card=' + encodeURIComponent(values(finForm).card)));
      if (!r) { finBox.replaceChildren(); return; }
      finBox.replaceChildren(h('p', { class: 'muted' }, `${r.member} (${r.cardNumber})`), fineRows(r.fines, loadFines, finReceipt));
    }
    finForm.addEventListener('submit', e => { e.preventDefault(); finReceipt.replaceChildren(); loadFines(); });

    app.append(h('h1', {}, 'Lend / Return book'),
      h('div', { class: 'grid2' },
        h('div', { class: 'card' }, h('h2', { style: 'margin-top:0' }, 'Lend books'), cardForm, infoBox, addForm, itemsBox, h('div', { class: 'actions' }, confirmBtn), lendMsg),
        h('div', { class: 'card' }, slipBox)),
      h('div', { class: 'card' }, h('h2', { style: 'margin-top:0' }, 'Return a book'), retForm, retBox, retReceipt),
      h('div', { class: 'card' }, h('h2', { style: 'margin-top:0' }, 'Collect fines'), finForm, finBox, finReceipt));
    renderItems();
  }
};

pages.books = {
  needs: needs.staff,
  async render(app, tab) {
    const tabs = [['books', 'Books'], ['authors', 'Authors'], ['categories', 'Categories'], ['publishers', 'Publishers']];
    const cur = tab || 'books';
    const body = h('div', {});
    app.append(h('h1', {}, 'Manage books'), h('div', { class: 'tabs' }, tabs.map(([k, t]) => h('button', { class: cur === k ? 'on' : '', onclick: () => go('#/books/' + k) }, t))), body);
    if (cur === 'books') await booksTab(body); else await refTab(body, cur);
  }
};

async function booksTab(body) {
  const st = { q: '', page: 1 };
  const list = h('div', {}), panel = h('div', {}), copiesBox = h('div', {});
  const search = h('input', { type: 'search', placeholder: 'Search books…', id: 'book-q', oninput: () => { st.q = search.value; st.page = 1; load(); } });
  async function load() {
    const r = await run(null, () => api('GET', '/api/books?' + new URLSearchParams({ q: st.q, page: st.page })));
    if (!r) return;
    list.replaceChildren(table(['ISBN', 'Title', 'Category', 'Copies (available/total)', ''], r.items.map(b => [b.isbn, b.title, b.category,
      `${b.availableCopies} / ${b.totalCopies}`, actions(
        h('button', { class: 'small', onclick: () => showForm(b) }, 'Edit'),
        h('button', { class: 'small secondary', onclick: () => showCopies(b) }, 'Copies'),
        h('button', { class: 'small danger', onclick: async () => {
          if (!confirm(`Delete "${b.title}"?`)) return;
          if (await run(null, () => api('DELETE', '/api/books/' + b.bookId)) !== undefined) { toast('Book deleted'); load(); }
        } }, 'Delete'))]), 'No books found'), r.total ? pager(r.page, r.totalPages, p => { st.page = p; load(); }) : null);
  }
  async function showForm(b) {
    const [cats, pubs] = await Promise.all([api('GET', '/api/categories'), api('GET', '/api/publishers')]);
    const form = h('form', { class: 'card', id: 'book-form' }, h('h2', { style: 'margin-top:0' }, b ? 'Edit book' : 'Add book'),
      h('div', { class: 'grid2' }, h('div', {},
        field('ISBN', 'isbn', { value: b && b.isbn }), field('Title', 'title', { value: b && b.title }),
        field('Authors (separate with commas)', 'authors', { value: b ? b.authors.join(', ') : '' }),
        field('Category', 'category', { value: b && b.category, list: 'dl-cat' })),
        h('div', {}, field('Publisher', 'publisher', { value: b && b.publisher, list: 'dl-pub' }), field('Publish year', 'publishYear', { type: 'number', value: b && b.publishYear }),
          b ? null : field('Number of copies', 'copies', { type: 'number', min: '0', value: 1 }), b ? null : field('Shelf location', 'shelfLocation', { placeholder: 'Shelf A1' }))),
      field('Description', 'description', { area: true, value: b && b.description }),
      h('datalist', { id: 'dl-cat' }, cats.map(c => h('option', { value: c.name }))), h('datalist', { id: 'dl-pub' }, pubs.map(p => h('option', { value: p.name }))),
      h('p', { class: 'form-error' }),
      h('div', { class: 'actions' }, h('button', { type: 'submit', id: 'btn-save-book' }, 'Save'), h('button', { type: 'button', class: 'secondary', onclick: () => panel.replaceChildren() }, 'Cancel')));
    form.addEventListener('submit', async e => {
      e.preventDefault();
      const v = values(form);
      const payload = { ...v, authors: v.authors.split(',').map(s => s.trim()).filter(Boolean), publishYear: v.publishYear ? Number(v.publishYear) : null, copies: v.copies === undefined || v.copies === '' ? null : Number(v.copies) };
      const r = await run(form, () => b ? api('PUT', '/api/books/' + b.bookId, payload) : api('POST', '/api/books', payload));
      if (r) { toast(b ? 'Book updated' : 'Book added'); panel.replaceChildren(); load(); }
    });
    panel.replaceChildren(form);
    form.scrollIntoView({ behavior: 'smooth' });
  }
  async function showCopies(b) {
    const cs = await api('GET', `/api/books/${b.bookId}/copies`);
    const addForm = h('form', { class: 'row' }, field('New copy - shelf location', 'shelfLocation'), h('div', { class: 'fixed' }, h('button', { type: 'submit', id: 'btn-add-copy' }, 'Add copy')));
    addForm.addEventListener('submit', async e => { e.preventDefault(); if (await run(null, () => api('POST', `/api/books/${b.bookId}/copies`, values(addForm)))) { toast('Copy added'); showCopies(b); load(); } });
    const rows = cs.map(c => {
      const shelf = h('input', { value: c.shelfLocation || '' });
      const locked = c.status === 'BORROWED' || c.status === 'RESERVED';
      const status = h('select', { disabled: locked }, [c.status, ...['AVAILABLE', 'DAMAGED', 'LOST'].filter(s => s !== c.status)].map(s => h('option', { value: s }, s)));
      return [c.barcode, locked ? badge(c.status) : status, shelf, actions(
        h('button', { class: 'small', onclick: async () => { if (await run(null, () => api('PUT', '/api/copies/' + c.copyId, { status: status.value, shelfLocation: shelf.value }))) { toast('Copy saved'); showCopies(b); load(); } } }, 'Save'),
        h('button', { class: 'small danger', onclick: async () => { if (!confirm('Delete copy ' + c.barcode + '?')) return; if (await run(null, () => api('DELETE', '/api/copies/' + c.copyId)) !== undefined) { toast('Copy deleted'); showCopies(b); load(); } } }, 'Delete'))];
    });
    copiesBox.replaceChildren(h('div', { class: 'card', id: 'copies-panel' }, h('h2', { style: 'margin-top:0' }, 'Copies of "' + b.title + '"'), table(['Barcode', 'Status', 'Shelf location', ''], rows, 'No copies'), addForm,
      h('div', { class: 'actions' }, h('button', { class: 'secondary', onclick: () => copiesBox.replaceChildren() }, 'Close'))));
    copiesBox.scrollIntoView({ behavior: 'smooth' });
  }
  body.append(h('div', { class: 'row card' }, search, h('div', { class: 'fixed' }, h('button', { id: 'btn-add-book', onclick: () => showForm(null) }, '+ Add Book'))), panel, list, copiesBox);
  await load();
}

async function refTab(body, kind) {
  const hasAddress = kind === 'publishers';
  const list = h('div', {});
  const addForm = h('form', { class: 'card row', id: 'ref-form' }, field('New name', 'name'), hasAddress ? field('Address', 'address') : null,
    h('div', { class: 'fixed' }, h('button', { type: 'submit', id: 'btn-add-ref' }, 'Add')), h('p', { class: 'form-error', style: 'flex-basis:100%' }));
  async function load() {
    const items = await api('GET', '/api/' + kind);
    list.replaceChildren(table(['Name', hasAddress ? 'Address' : null, ''].filter(x => x !== null), items.map(it => {
      const name = h('input', { value: it.name }), addr = hasAddress ? h('input', { value: it.address || '' }) : null;
      return [name, addr, actions(
        h('button', { class: 'small', onclick: async () => { if (await run(null, () => api('PUT', `/api/${kind}/${it.id}`, { name: name.value, address: addr ? addr.value : undefined }))) { toast('Saved'); load(); } } }, 'Save'),
        h('button', { class: 'small danger', onclick: async () => { if (!confirm('Delete "' + it.name + '"?')) return; if (await run(null, () => api('DELETE', `/api/${kind}/${it.id}`)) !== undefined) { toast('Deleted'); load(); } } }, 'Delete'))].filter(x => x !== null);
    }), 'Nothing here yet'));
  }
  addForm.addEventListener('submit', async e => { e.preventDefault(); if (await run(addForm, () => api('POST', '/api/' + kind, values(addForm)))) { toast('Added'); addForm.reset(); load(); } });
  body.append(addForm, list);
  await load();
}

pages.members = {
  needs: needs.staff,
  async render(app) {
    const list = h('div', {}), panel = h('div', {});
    const search = h('input', { type: 'search', id: 'member-q', placeholder: 'Search by name, username or card number…', oninput: load });
    async function load() {
      const ms = await run(null, () => api('GET', '/api/members?q=' + encodeURIComponent(search.value)));
      if (!ms) return;
      const today = todayIso();
      list.replaceChildren(table(['Name', 'Username', 'Card', 'Expiry', 'Status', ''], ms.map(m => [m.fullName, m.username, m.cardNumber || '—', fmtDate(m.cardExpiry), badge(m.status),
        actions(
          (m.status === 'PENDING' || (m.status === 'ACTIVE' && m.cardExpiry && m.cardExpiry < today)) ? h('button', { class: 'small', onclick: () => activate(m) }, 'Activate card') : null,
          h('button', { class: 'small secondary', onclick: () => edit(m) }, 'Edit'),
          m.status === 'LOCKED' ? h('button', { class: 'small secondary', onclick: () => act(`/api/members/${m.userId}/unlock`, 'POST', 'Account unlocked') }, 'Unlock')
            : h('button', { class: 'small secondary', onclick: () => act(`/api/members/${m.userId}/lock`, 'POST', 'Account locked') }, 'Lock'),
          h('button', { class: 'small danger', onclick: () => { if (confirm(`Delete member ${m.fullName}?`)) act('/api/members/' + m.userId, 'DELETE', 'Member deleted'); } }, 'Delete'))]), 'No members found'));
    }
    async function act(path, method, okMsg) { if (await run(null, () => api(method, path)) !== undefined) { toast(okMsg); load(); } }
    function activate(m) {
      const form = h('form', { class: 'card', id: 'activate-form' }, h('h2', { style: 'margin-top:0' }, 'Issue library card for ' + m.fullName),
        field('Card number', 'cardNumber', { placeholder: 'LIB-000127' }), h('p', { class: 'muted' }, 'The card is valid for 1 year from today.'), h('p', { class: 'form-error' }),
        h('div', { class: 'actions' }, h('button', { type: 'submit', id: 'btn-activate' }, 'Activate'), h('button', { type: 'button', class: 'secondary', onclick: () => panel.replaceChildren() }, 'Cancel')));
      form.addEventListener('submit', async e => { e.preventDefault(); const r = await run(form, () => api('POST', `/api/members/${m.userId}/activate`, values(form))); if (r) { toast(r.message); panel.replaceChildren(); load(); } });
      panel.replaceChildren(form);
    }
    function edit(m) {
      const form = h('form', { class: 'card', id: 'member-form' }, h('h2', { style: 'margin-top:0' }, 'Edit member'),
        h('div', { class: 'grid2' }, h('div', {}, field('Full name', 'fullName', { value: m.fullName }), field('Phone', 'phone', { value: m.phone }), field('Email', 'email', { value: m.email })),
          h('div', {}, field('Address', 'address', { value: m.address }), field('Date of birth', 'dateOfBirth', { type: 'date', value: m.dateOfBirth }))),
        h('p', { class: 'form-error' }), h('div', { class: 'actions' }, h('button', { type: 'submit', id: 'btn-save-member' }, 'Save'), h('button', { type: 'button', class: 'secondary', onclick: () => panel.replaceChildren() }, 'Cancel')));
      form.addEventListener('submit', async e => { e.preventDefault(); if (await run(form, () => api('PUT', '/api/members/' + m.userId, values(form)))) { toast('Member updated'); panel.replaceChildren(); load(); } });
      panel.replaceChildren(form);
    }
    app.append(h('h1', {}, 'Manage members'), h('div', { class: 'card' }, search), panel, list);
    await load();
  }
};

// =================================================================== ADMIN PAGES
pages.staff = {
  needs: needs.admin,
  async render(app) {
    const list = h('div', {}), panel = h('div', {});
    async function load() {
      const ss = await api('GET', '/api/staff');
      list.replaceChildren(table(['Name', 'Username', 'Role', 'Staff code', 'Status', ''], ss.map(s => [s.fullName, s.username, s.role, s.staffCode, badge(s.status),
        actions(h('button', { class: 'small secondary', onclick: () => form(s) }, 'Edit'),
          s.userId === session.user.userId ? null : (s.status === 'LOCKED'
            ? h('button', { class: 'small secondary', onclick: () => act(`/api/staff/${s.userId}/unlock`, 'POST', 'Account unlocked') }, 'Unlock')
            : h('button', { class: 'small secondary', onclick: () => act(`/api/staff/${s.userId}/lock`, 'POST', 'Account locked') }, 'Lock')),
          h('button', { class: 'small danger', onclick: () => { if (confirm(`Delete ${s.fullName}?`)) act('/api/staff/' + s.userId, 'DELETE', 'Account deleted'); } }, 'Delete'))]), 'No staff'));
    }
    async function act(path, method, okMsg) { if (await run(null, () => api(method, path)) !== undefined) { toast(okMsg); load(); } }
    function form(s) {
      const f = h('form', { class: 'card', id: 'staff-form' }, h('h2', { style: 'margin-top:0' }, s ? 'Edit staff' : 'Add staff'),
        h('div', { class: 'grid2' }, h('div', {}, field('Full name', 'fullName', { value: s && s.fullName }), field('Email', 'email', { value: s && s.email }), field('Phone', 'phone', { value: s && s.phone }),
          field('Staff code', 'staffCode', { value: s && s.staffCode })),
          h('div', {}, field('Username', 'username', { value: s && s.username, readonly: !!s }), s ? null : field('Password', 'password', { type: 'password', autocomplete: 'new-password' }),
            s ? null : field('Role', 'role', { options: ['LIBRARIAN', 'ADMIN'], value: 'LIBRARIAN' }), field('Hire date', 'hireDate', { type: 'date', value: s && s.hireDate }))),
        h('p', { class: 'form-error' }), h('div', { class: 'actions' }, h('button', { type: 'submit', id: 'btn-save-staff' }, 'Save'), h('button', { type: 'button', class: 'secondary', onclick: () => panel.replaceChildren() }, 'Cancel')));
      f.addEventListener('submit', async e => { e.preventDefault(); const r = await run(f, () => s ? api('PUT', '/api/staff/' + s.userId, values(f)) : api('POST', '/api/staff', values(f))); if (r) { toast('Saved'); panel.replaceChildren(); load(); } });
      panel.replaceChildren(f);
    }
    app.append(h('h1', {}, 'Manage staff'), h('div', { class: 'actions', style: 'margin:0 0 12px' }, h('button', { id: 'btn-add-staff', onclick: () => form(null) }, '+ Add staff')), panel, list);
    await load();
  }
};

pages.reports = {
  needs: needs.admin,
  async render(app) {
    const first = new Date(); first.setDate(1);
    const form = h('form', { class: 'card', id: 'report-form' }, h('div', { class: 'row' },
      field('Report', 'type', { options: [['most-borrowed', 'Most borrowed books'], ['overdue', 'Overdue loans'], ['fines', 'Fines collected'], ['members', 'Member statistics']], value: 'most-borrowed' }),
      field('From', 'from', { type: 'date', value: first.toISOString().slice(0, 10) }), field('To', 'to', { type: 'date', value: todayIso() }),
      h('div', { class: 'fixed' }, h('button', { type: 'submit', id: 'btn-view-report' }, 'View')),
      h('div', { class: 'fixed' }, h('button', { type: 'button', class: 'secondary', id: 'btn-export', onclick: exportCsv }, 'Export CSV'))), h('p', { class: 'form-error' }));
    const out = h('div', { id: 'report-out' });
    async function exportCsv() {
      const v = values(form);
      const res = await run(form, () => api('GET', `/api/reports/${v.type}?from=${v.from}&to=${v.to}&format=csv`, undefined, true));
      if (!res) return;
      const blob = await res.blob();
      const a = h('a', { href: URL.createObjectURL(blob), download: `${v.type}-${v.from}-${v.to}.csv` });
      document.body.append(a); a.click(); a.remove();
    }
    form.addEventListener('submit', async e => {
      e.preventDefault();
      out.replaceChildren();
      const v = values(form);
      const r = await run(form, () => api('GET', `/api/reports/${v.type}?from=${v.from}&to=${v.to}`));
      if (!r) return;
      const max = Math.max(1, ...r.chartValues);
      out.replaceChildren(h('h2', {}, r.title), r.empty ? h('div', { class: 'card empty', id: 'no-data' }, 'No data') : table(r.columns, r.rows.map(row => row.map(c => c === null ? '—' : String(c)))),
        Object.entries(r.summary).map(([k, val]) => h('p', {}, h('strong', {}, k + ': '), typeof val === 'number' && /VND/.test(k) ? money(val) : String(val))),
        r.chartLabels.length ? h('div', { class: 'card bars' }, r.chartLabels.map((l, i) => h('div', { class: 'bar-row' }, h('div', { class: 'name', title: l }, l),
          h('div', { class: 'bar', style: `width:${Math.max(1, Math.round(r.chartValues[i] / max * 60))}%` }), h('span', {}, String(r.chartValues[i]))))) : null);
    });
    const cfg = await api('GET', '/api/config');
    const cfgForm = h('form', { class: 'card row', id: 'config-form' }, field('Fine per overdue day per book (VND)', 'finePerDay', { type: 'number', min: '1', value: cfg.finePerDay }),
      h('div', { class: 'fixed' }, h('button', { type: 'submit', id: 'btn-save-config' }, 'Save setting')), h('p', { class: 'form-error', style: 'flex-basis:100%' }));
    cfgForm.addEventListener('submit', async e => { e.preventDefault(); const r = await run(cfgForm, () => api('PUT', '/api/config', { finePerDay: Number(values(cfgForm).finePerDay) })); if (r) toast(r.message); });
    app.append(h('h1', {}, 'Reports'), form, out, h('h2', {}, 'Settings'), cfgForm);
  }
};

// =================================================================== start
window.addEventListener('hashchange', route);
(async function start() {
  if (session.token) {
    try { session.user = await api('GET', '/api/me'); }
    catch (e) { session.set(null, null); }
  }
  route();
})();
