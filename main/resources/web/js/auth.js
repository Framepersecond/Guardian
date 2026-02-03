// Guardian Auth Page Logic

document.addEventListener('DOMContentLoaded', async () => {
    // Check if setup is completed first
    try {
        const response = await fetch('/api/setup/status');
        const data = await response.json();
        if (!data.completed) {
            window.location.href = '/setup.html';
            return;
        }
    } catch (error) {
        console.error('Error checking setup status:', error);
    }

    // Check if already logged in
    if (api.isLoggedIn()) {
        window.location.href = '/dashboard.html';
        return;
    }

    // Tab switching
    const tabs = document.querySelectorAll('.tab');
    const loginForm = document.getElementById('loginForm');
    const registerForm = document.getElementById('registerForm');

    tabs.forEach(tab => {
        tab.addEventListener('click', () => {
            tabs.forEach(t => {
                t.classList.remove('active');
                t.classList.add('text-slate-400');
            });
            tab.classList.add('active');
            tab.classList.remove('text-slate-400');

            if (tab.dataset.tab === 'login') {
                loginForm.classList.add('active');
                registerForm.classList.remove('active');
            } else {
                loginForm.classList.remove('active');
                registerForm.classList.add('active');
            }
        });
    });

    // Login form
    loginForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const errorEl = document.getElementById('loginError');
        errorEl.classList.add('hidden');

        const username = document.getElementById('loginUsername').value;
        const password = document.getElementById('loginPassword').value;

        try {
            await api.login(username, password);
            window.location.href = '/dashboard.html';
        } catch (error) {
            errorEl.textContent = error.message;
            errorEl.classList.remove('hidden');
        }
    });

    // Register form
    registerForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const errorEl = document.getElementById('registerError');
        errorEl.classList.add('hidden');

        const code = document.getElementById('registerCode').value.toUpperCase();
        const username = document.getElementById('registerUsername').value;
        const password = document.getElementById('registerPassword').value;
        const passwordConfirm = document.getElementById('registerPasswordConfirm').value;

        if (password !== passwordConfirm) {
            errorEl.textContent = 'Passwörter stimmen nicht überein.';
            errorEl.classList.remove('hidden');
            return;
        }

        try {
            await api.register(code, username, password);
            window.location.href = '/dashboard.html';
        } catch (error) {
            errorEl.textContent = error.message;
            errorEl.classList.remove('hidden');
        }
    });
});
