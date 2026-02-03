// Guardian Setup Wizard

let currentStep = 1;
const totalSteps = 4;
let config = {};

// Initialize on page load
document.addEventListener('DOMContentLoaded', async () => {
    await checkSetupStatus();
    await loadConfig();
    loadTimezones();
    setupEventListeners();
});

// Check if setup is already completed
async function checkSetupStatus() {
    try {
        const response = await fetch('/api/setup/status');
        const data = await response.json();

        if (data.completed) {
            // Setup already done, redirect to login
            window.location.href = '/index.html';
        }
    } catch (error) {
        console.error('Error checking setup status:', error);
    }
}

// Load current config
async function loadConfig() {
    try {
        const response = await fetch('/api/setup/config');
        if (response.ok) {
            config = await response.json();
            populateFields();
        }
    } catch (error) {
        console.error('Error loading config:', error);
    }
}

// Populate form fields with config values
function populateFields() {
    document.getElementById('webPort').value = config.webPort || 6746;
    document.getElementById('webHost').value = config.webHost || '0.0.0.0';
    document.getElementById('timezone').value = config.timezone || 'Europe/Berlin';
    document.getElementById('mapEnabled').checked = config.mapEnabled !== false;
    document.getElementById('bluemapUrl').value = config.bluemapUrl || '';
    document.getElementById('markerInterval').value = config.markerUpdateInterval || 5;

    // Populate worlds dropdown
    if (config.availableWorlds && config.availableWorlds.length > 0) {
        const worldSelect = document.getElementById('defaultWorld');
        worldSelect.innerHTML = config.availableWorlds
            .map(w => `<option value="${w}" ${w === config.defaultWorld ? 'selected' : ''}>${w}</option>`)
            .join('');
    }

    // Don't pre-fill JWT secret if it's the default
    if (config.jwtSecret && config.jwtSecret !== 'CHANGE-ME-TO-SOMETHING-RANDOM-AND-SECURE') {
        document.getElementById('jwtSecret').value = config.jwtSecret;
    }
}

// Load timezones
async function loadTimezones() {
    try {
        const response = await fetch('/api/setup/timezones');
        if (response.ok) {
            const timezones = await response.json();
            const select = document.getElementById('timezone');
            select.innerHTML = timezones
                .map(tz => `<option value="${tz}" ${tz === config.timezone ? 'selected' : ''}>${tz}</option>`)
                .join('');
        }
    } catch (error) {
        console.error('Error loading timezones:', error);
    }
}

// Setup event listeners
function setupEventListeners() {
    // BlueMap toggle
    document.getElementById('mapEnabled').addEventListener('change', (e) => {
        const settings = document.getElementById('bluemapSettings');
        settings.style.opacity = e.target.checked ? '1' : '0.5';
        settings.style.pointerEvents = e.target.checked ? 'auto' : 'none';
    });
}

// Navigation
function nextStep() {
    if (currentStep === 2) {
        // Validate step 2
        const jwtSecret = document.getElementById('jwtSecret').value;
        if (jwtSecret.length < 16) {
            showError('JWT Secret muss mindestens 16 Zeichen lang sein!');
            document.getElementById('jwtSecret').focus();
            return;
        }
    }

    if (currentStep < totalSteps) {
        goToStep(currentStep + 1);
    }
}

function prevStep() {
    if (currentStep > 1) {
        goToStep(currentStep - 1);
    }
}

function goToStep(step) {
    // Hide current step
    document.querySelector(`.step[data-step="${currentStep}"]`).classList.remove('active');

    // Update indicators
    for (let i = 1; i <= totalSteps; i++) {
        const indicator = document.querySelector(`.step-indicator[data-step="${i}"]`);
        indicator.classList.remove('active', 'completed');

        if (i < step) {
            indicator.classList.add('completed');
        } else if (i === step) {
            indicator.classList.add('active');
        }
    }

    // Show new step
    currentStep = step;
    document.querySelector(`.step[data-step="${currentStep}"]`).classList.add('active');

    // Update summary on last step
    if (step === 4) {
        updateSummary();
    }
}

// Generate random JWT secret
function generateSecret() {
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*';
    let secret = '';
    for (let i = 0; i < 32; i++) {
        secret += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    document.getElementById('jwtSecret').value = secret;
}

// Test BlueMap connection
async function testBlueMap() {
    const url = document.getElementById('bluemapUrl').value;
    const testBtn = document.getElementById('testBtn');
    const resultEl = document.getElementById('testResult');

    if (!url) {
        resultEl.textContent = 'Bitte gib eine URL ein.';
        resultEl.className = 'text-sm mt-2 text-amber-400';
        resultEl.classList.remove('hidden');
        return;
    }

    testBtn.disabled = true;
    testBtn.innerHTML = '<span class="material-symbols-outlined align-middle animate-spin">sync</span>';

    try {
        const response = await fetch(`/api/setup/test-bluemap?url=${encodeURIComponent(url)}`);
        const data = await response.json();

        if (response.ok) {
            resultEl.textContent = '✓ ' + data.message;
            resultEl.className = 'text-sm mt-2 text-emerald-400';
        } else {
            resultEl.textContent = '✗ ' + data.error;
            resultEl.className = 'text-sm mt-2 text-red-400';
        }
    } catch (error) {
        resultEl.textContent = '✗ Verbindungsfehler';
        resultEl.className = 'text-sm mt-2 text-red-400';
    }

    resultEl.classList.remove('hidden');
    testBtn.disabled = false;
    testBtn.innerHTML = '<span class="material-symbols-outlined align-middle">lan</span> Testen';
}

// Update summary on final step
function updateSummary() {
    document.getElementById('summaryPort').textContent = document.getElementById('webPort').value;
    document.getElementById('summaryTimezone').textContent = document.getElementById('timezone').value;
    document.getElementById('summaryBlueMap').textContent = document.getElementById('mapEnabled').checked ? 'Aktiviert' : 'Deaktiviert';
}

// Complete setup
async function completeSetup() {
    const completeBtn = document.getElementById('completeBtn');
    const loadingOverlay = document.getElementById('loadingOverlay');

    // Gather all settings
    const settings = {
        webPort: parseInt(document.getElementById('webPort').value),
        webHost: document.getElementById('webHost').value,
        jwtSecret: document.getElementById('jwtSecret').value,
        timezone: document.getElementById('timezone').value,
        mapEnabled: document.getElementById('mapEnabled').checked,
        bluemapUrl: document.getElementById('bluemapUrl').value,
        defaultWorld: document.getElementById('defaultWorld').value,
        markerUpdateInterval: parseInt(document.getElementById('markerInterval').value)
    };

    // Validate
    if (settings.jwtSecret.length < 16) {
        showError('JWT Secret muss mindestens 16 Zeichen lang sein!');
        goToStep(2);
        return;
    }

    // Show loading
    completeBtn.disabled = true;
    loadingOverlay.classList.remove('hidden');

    try {
        const response = await fetch('/api/setup/save', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(settings)
        });

        const data = await response.json();

        if (response.ok) {
            // Success! Show message and redirect
            loadingOverlay.innerHTML = `
                <div class="text-center">
                    <div class="w-16 h-16 mx-auto rounded-full bg-emerald-500 flex items-center justify-center mb-4">
                        <span class="material-symbols-outlined text-white text-3xl">check</span>
                    </div>
                    <p class="text-white text-lg font-medium mb-2">Setup erfolgreich!</p>
                    <p class="text-slate-400">Du wirst weitergeleitet...</p>
                </div>
            `;

            setTimeout(() => {
                window.location.href = '/index.html';
            }, 2000);
        } else {
            loadingOverlay.classList.add('hidden');
            completeBtn.disabled = false;
            showError(data.error || 'Fehler beim Speichern');
        }
    } catch (error) {
        loadingOverlay.classList.add('hidden');
        completeBtn.disabled = false;
        showError('Verbindungsfehler: ' + error.message);
    }
}

// Show error toast
function showError(message) {
    // Create toast element
    const toast = document.createElement('div');
    toast.className = 'fixed bottom-4 right-4 bg-red-500/90 backdrop-blur-sm text-white px-6 py-3 rounded-xl shadow-lg flex items-center gap-2 z-50 animate-pulse';
    toast.innerHTML = `
        <span class="material-symbols-outlined">error</span>
        ${message}
    `;

    document.body.appendChild(toast);

    setTimeout(() => {
        toast.style.opacity = '0';
        setTimeout(() => toast.remove(), 300);
    }, 4000);
}
