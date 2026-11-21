// No third-party scripts, telemetry, cookies or local storage. Passwords go only to Firebase.
export function deletionPage() {
 const nonce=crypto.randomUUID().replaceAll('-','');
 const html=`<!doctype html><html lang="ru"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>TestHelper — удаление аккаунта</title><style>body{font:18px system-ui;max-width:620px;margin:48px auto;padding:20px;background:#101923;color:#e1eaf5}label{display:block;margin:16px 0}input,button{font:inherit;padding:12px;box-sizing:border-box;width:100%}button{background:#a6d37b;border:0;border-radius:8px}p{line-height:1.6}</style>
<h1>Удаление аккаунта TestHelper</h1><p>Будет удалён аккаунт Firebase: электронная почта, имя и данные входа. Доступ к ИИ прекратится. Локальные тесты, карточки и история останутся на вашем телефоне. Сервер не хранит документы; агрегированные счётчики IP/общих лимитов по хешам автоматически истекают после смены дня.</p>
<p>Если вы забыли пароль, сначала восстановите его в приложении: Настройки → Личный кабинет → Восстановить пароль.</p>
<form id="remove"><label>Электронная почта<input id="email" type="email" autocomplete="username" maxlength="254" required></label><label>Пароль<input id="password" type="password" autocomplete="current-password" required></label><button id="submit">Удалить мой аккаунт</button></form><p role="status" id="status"></p>
<script nonce="${nonce}" type="module">
const form=document.querySelector('#remove'),status=document.querySelector('#status'),button=document.querySelector('#submit');
form.addEventListener('submit',async event=>{
 event.preventDefault();if(!confirm('Удалить аккаунт TestHelper без возможности восстановления?'))return;
 button.disabled=true;status.textContent='Удаление…';
 try {
  const conf=await fetch('/api/v1/auth/config',{cache:'no-store'});if(!conf.ok)throw Error();const {apiKey}=await conf.json();
  const call=async(action,body)=>{const response=await fetch('https://identitytoolkit.googleapis.com/v1/accounts:'+action+'?key='+encodeURIComponent(apiKey),{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body),redirect:'error'});if(!response.ok)throw Error();return response.json();};
  const session=await call('signInWithPassword',{email:document.querySelector('#email').value.trim(),password:document.querySelector('#password').value,returnSecureToken:true});
  document.querySelector('#password').value='';
  const deleted=await fetch('/api/v1/account',{method:'DELETE',headers:{Authorization:'Bearer '+session.idToken},redirect:'error'});if(!deleted.ok)throw Error();form.reset();form.hidden=true;status.textContent='Аккаунт удалён. Данные на устройстве сохранены.';
 } catch {status.textContent='Не удалось удалить аккаунт. Проверьте почту, пароль и соединение или восстановите пароль в приложении.';}
 finally {button.disabled=false;document.querySelector('#password').value='';}
});</script></html>`;
 return new Response(html,{headers:{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Referrer-Policy':'no-referrer',
 'Content-Security-Policy':`default-src 'none'; script-src 'nonce-${nonce}'; connect-src 'self' https://identitytoolkit.googleapis.com; style-src 'unsafe-inline'; form-action 'none'; frame-ancestors 'none'; base-uri 'none'`}});
}
