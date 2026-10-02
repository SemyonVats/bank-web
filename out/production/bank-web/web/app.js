let selectedPerson = null;
const personPanel = document.querySelector('#person-panel');
const emptyState = document.querySelector('#empty-state');
const accountsElement = document.querySelector('#accounts');
const toast = document.querySelector('#toast');

async function request(path, options = {}) {
  const response = await fetch(path, options);
  const body = await response.json();
  if (!response.ok || !body.ok) throw new Error(body.error || 'Ошибка запроса');
  return body.data;
}

function formBody(form) {
  return new URLSearchParams(new FormData(form));
}

function notify(message, error = false) {
  toast.textContent = message;
  toast.className = `toast visible${error ? ' error' : ''}`;
  clearTimeout(notify.timer);
  notify.timer = setTimeout(() => toast.className = 'toast', 3200);
}

function selectPerson(person) {
  selectedPerson = person;
  document.querySelector('#person-name').textContent = `${person.firstName} ${person.lastName}`;
  document.querySelector('#person-passport').textContent = person.passport;
  emptyState.classList.add('hidden');
  personPanel.classList.remove('hidden');
  refreshAccounts();
}

async function refreshAccounts() {
  if (!selectedPerson) return;
  try {
    const accounts = await request(`/api/accounts?passport=${encodeURIComponent(selectedPerson.passport)}`);
    accountsElement.innerHTML = accounts.length
      ? accounts.map(accountTemplate).join('')
      : '<div class="empty-state" style="height:170px"><p>Счетов пока нет.</p></div>';
    bindAccountForms();
  } catch (error) { notify(error.message, true); }
}

function accountTemplate(account) {
  return `<div class="account" data-sub-id="${escapeHtml(account.subId)}">
    <div class="account-head">
      <div><small>Счёт</small><div class="account-id">${escapeHtml(account.id)}</div></div>
      <div><small>Баланс</small><div class="balance">${account.balance}</div></div>
    </div>
    <div class="operations">
      <form class="change-form">
        <input name="delta" type="number" placeholder="+100 или -50" required>
        <button>Изменить</button>
      </form>
      <form class="set-form">
        <input name="amount" type="number" min="0" placeholder="Новый баланс" required>
        <button class="secondary">Установить</button>
      </form>
    </div>
  </div>`;
}

function bindAccountForms() {
  document.querySelectorAll('.account').forEach(card => {
    card.querySelector('.change-form').addEventListener('submit', event => updateAccount(event, card, 'change'));
    card.querySelector('.set-form').addEventListener('submit', event => updateAccount(event, card, 'set'));
  });
}

async function updateAccount(event, card, action) {
  event.preventDefault();
  const body = formBody(event.currentTarget);
  body.set('passport', selectedPerson.passport);
  body.set('subId', card.dataset.subId);
  try {
    await request(`/api/account/${action}`, { method: 'POST', body });
    event.currentTarget.reset();
    await refreshAccounts();
    notify('Баланс обновлён');
  } catch (error) { notify(error.message, true); }
}

document.querySelector('#person-form').addEventListener('submit', async event => {
  event.preventDefault();
  try {
    const person = await request('/api/person/create', { method: 'POST', body: formBody(event.currentTarget) });
    selectPerson(person);
    notify('Клиент готов к работе');
  } catch (error) { notify(error.message, true); }
});

document.querySelector('#person-search').addEventListener('submit', async event => {
  event.preventDefault();
  const passport = new FormData(event.currentTarget).get('passport');
  try {
    selectPerson(await request(`/api/person?passport=${encodeURIComponent(passport)}`));
    notify('Клиент найден');
  } catch (error) { notify(error.message, true); }
});

document.querySelector('#account-form').addEventListener('submit', async event => {
  event.preventDefault();
  const body = formBody(event.currentTarget);
  body.set('passport', selectedPerson.passport);
  try {
    await request('/api/account/create', { method: 'POST', body });
    event.currentTarget.reset();
    await refreshAccounts();
    notify('Счёт открыт');
  } catch (error) { notify(error.message, true); }
});

function escapeHtml(value) {
  const element = document.createElement('span');
  element.textContent = value;
  return element.innerHTML;
}
