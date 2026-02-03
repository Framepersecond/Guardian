// Guardian Dashboard Logic - Premium Redesign

let currentBlockPage = 1;
let currentContainerPage = 1;
let appConfig = { blueMapEnabled: false, blueMapUrl: '', defaultWorld: 'world' };

// Heatmap state - Simplified
let heatmapData = [];

document.addEventListener('DOMContentLoaded', () => {
    // Check auth
    if (!api.isLoggedIn()) {
        window.location.href = '/';
        return;
    }

    // Initialize logic
    loadConfigAndUser();



    // Section navigation
    const navItems = document.querySelectorAll('.nav-item');
    const sections = document.querySelectorAll('.section');

    navItems.forEach(item => {
        item.addEventListener('click', (e) => {
            e.preventDefault();
            const sectionId = item.dataset.section;
            if (!sectionId) return;

            // Update nav active state
            navItems.forEach(i => i.classList.remove('nav-item-active', 'text-primary'));
            navItems.forEach(i => i.classList.add('text-slate-400'));
            item.classList.add('nav-item-active');
            item.classList.remove('text-slate-400');

            // Show section
            sections.forEach(s => s.classList.remove('active'));
            const section = document.getElementById(sectionId);
            if (section) {
                section.classList.add('active');
            }

            // Scroll to top on section change
            const contentScroll = document.getElementById('contentScroll');
            if (contentScroll) contentScroll.scrollTop = 0;

            // Update page title
            const titles = {
                'dashboard': 'Dashboard',
                'logs': 'Server Logs',
                'analytics': 'Analytics',
                'admin': 'Administration',
                'settings': 'Einstellungen'
            };
            document.getElementById('pageTitle').textContent = titles[sectionId] || sectionId;

            // Load section data
            loadSection(sectionId);
        });
    });

    // Initialize heatmap
    initHeatmap();

    // Load worlds for dropdowns
    loadWorlds();

    // Initial load
    loadSection('dashboard');
});

async function loadConfigAndUser() {
    try {
        // Load user info first (critical)
        const user = await api.getMe();
        if (!user) {
            window.location.href = '/';
            return;
        }

        // Update sidebar
        document.getElementById('sidebarUsername').textContent = user.minecraftName || user.username;
        document.getElementById('sidebarRole').textContent = user.role === 'admin' ? 'Administrator' : 'Benutzer';

        // Setup Avatar
        const avatarImg = document.getElementById('userAvatarImg');
        const avatarIcon = document.getElementById('userAvatarIcon');
        const avatarUrl = `https://minotar.net/helm/${user.minecraftName}/100.png`;

        if (avatarImg) { avatarImg.src = avatarUrl; avatarImg.classList.remove('hidden'); }
        if (avatarIcon) avatarIcon.classList.add('hidden');

        if (user.role === 'admin') {
            document.body.classList.add('is-admin');
        }

        // Load config
        const config = await api.getConfig();
        appConfig = config || appConfig; // Fallback to default if null

        // Config loaded successfully
    } catch (e) {
        console.error("Init failed", e);
    }
}

// Analytics Charts
let timelineChart = null;
let peakHoursChart = null;
let blockTypesChart = null;
let customChart = null;

// Chart color scheme
const chartColors = {
    primary: '#00f0e8',
    secondary: '#8b5cf6',
    accent: '#fbbf24',
    danger: '#ef4444',
    success: '#22c55e',
    gradients: ['#00f0e8', '#0984e3', '#8b5cf6', '#d63384', '#dc3545', '#fd7e14', '#ffc107', '#20c997']
};

async function initAnalytics() {
    console.log('Initializing Analytics...');

    // Setup event listeners
    document.getElementById('refreshAnalytics')?.addEventListener('click', loadAllAnalytics);
    document.getElementById('analyticsPeriod')?.addEventListener('change', loadAllAnalytics);
    document.getElementById('generateCustomChart')?.addEventListener('click', generateCustomChart);

    // Show admin section if admin
    const userRole = localStorage.getItem('userRole');
    if (userRole === 'admin') {
        const adminSection = document.getElementById('adminHotspotsSection');
        if (adminSection) adminSection.style.display = 'block';
    }

    // Load all analytics data
    await loadAllAnalytics();
}

async function loadAllAnalytics() {
    const period = document.getElementById('analyticsPeriod')?.value || '7d';

    await Promise.all([
        loadPeakHoursChart(),
        loadBlockTypesChart(),
        loadAdminHotspots()
    ]);
}

async function loadTimelineChart(period) {
    try {
        // Calculate time range based on period
        const now = Math.floor(Date.now() / 1000);
        let from = now - 24 * 60 * 60;
        if (period === '7d') from = now - 7 * 24 * 60 * 60;
        if (period === '30d') from = now - 30 * 24 * 60 * 60;

        const data = await api.getTimelineStats(from, now);

        const ctx = document.getElementById('timelineChart')?.getContext('2d');
        if (!ctx) return;

        if (timelineChart) timelineChart.destroy();

        timelineChart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: data.map(d => d.timeSlot),
                datasets: [{
                    label: 'Blöcke',
                    data: data.map(d => d.blockCount),
                    borderColor: chartColors.primary,
                    backgroundColor: 'rgba(0, 240, 232, 0.1)',
                    fill: true,
                    tension: 0.4
                }, {
                    label: 'Container',
                    data: data.map(d => d.containerCount),
                    borderColor: chartColors.secondary,
                    backgroundColor: 'rgba(139, 92, 246, 0.1)',
                    fill: true,
                    tension: 0.4
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { labels: { color: '#94a3b8' } } },
                scales: {
                    x: { ticks: { color: '#64748b' }, grid: { color: 'rgba(255,255,255,0.05)' } },
                    y: { ticks: { color: '#64748b' }, grid: { color: 'rgba(255,255,255,0.05)' } }
                }
            }
        });
    } catch (error) {
        console.error('Failed to load timeline:', error);
    }
}

async function loadPeakHoursChart() {
    try {
        const data = await api.request('/stats/peak-hours');

        const ctx = document.getElementById('peakHoursChart')?.getContext('2d');
        if (!ctx) return;

        if (peakHoursChart) peakHoursChart.destroy();

        const labels = Array.from({ length: 24 }, (_, i) => `${i}:00`);
        const values = labels.map((_, i) => data[i] || 0);

        peakHoursChart = new Chart(ctx, {
            type: 'bar',
            data: {
                labels: labels,
                datasets: [{
                    label: 'Aktionen',
                    data: values,
                    backgroundColor: values.map((v, i) => {
                        // Highlight peak hours
                        const maxVal = Math.max(...values);
                        if (v === maxVal) return chartColors.danger;
                        if (v > maxVal * 0.7) return chartColors.accent;
                        return chartColors.primary;
                    }),
                    borderRadius: 4
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { display: false } },
                scales: {
                    x: { ticks: { color: '#64748b' }, grid: { display: false } },
                    y: { ticks: { color: '#64748b' }, grid: { color: 'rgba(255,255,255,0.05)' } }
                }
            }
        });
    } catch (error) {
        console.error('Failed to load peak hours:', error);
    }
}

async function loadTopPlayers() {
    try {
        const players = await api.request('/stats/top-players?limit=10');

        const container = document.getElementById('topPlayersList');
        if (!container) return;

        if (!players || players.length === 0) {
            container.innerHTML = '<div class="text-slate-500 text-sm">Keine Daten verfügbar</div>';
            return;
        }

        const maxActions = Math.max(...players.map(p => p.totalActions));

        container.innerHTML = players.map((player, index) => {
            const percentage = (player.totalActions / maxActions) * 100;
            const medal = index === 0 ? '🥇' : index === 1 ? '🥈' : index === 2 ? '🥉' : '';

            return `
                <div class="flex items-center gap-3 p-2 rounded-lg bg-white/5">
                    <span class="text-lg w-8 text-center">${medal || (index + 1)}</span>
                    <div class="flex-1">
                        <div class="flex justify-between text-sm">
                            <span class="text-white font-medium">${player.playerName}</span>
                            <span class="text-slate-400">${player.totalActions.toLocaleString()}</span>
                        </div>
                        <div class="mt-1 h-1.5 bg-white/10 rounded-full overflow-hidden">
                            <div class="h-full rounded-full transition-all" style="width: ${percentage}%; background: linear-gradient(90deg, ${chartColors.primary}, ${chartColors.secondary})"></div>
                        </div>
                        <div class="flex gap-3 text-xs text-slate-500 mt-1">
                            <span>⛏ ${player.blocksBroken}</span>
                            <span>🧱 ${player.blocksPlaced}</span>
                        </div>
                    </div>
                </div>
            `;
        }).join('');
    } catch (error) {
        console.error('Failed to load top players:', error);
    }
}

async function loadBlockTypesChart() {
    try {
        const data = await api.request('/stats/block-types');

        const ctx = document.getElementById('blockTypesChart')?.getContext('2d');
        if (!ctx) return;

        if (blockTypesChart) blockTypesChart.destroy();

        const entries = Object.entries(data).slice(0, 8);

        blockTypesChart = new Chart(ctx, {
            type: 'doughnut',
            data: {
                labels: entries.map(([name]) => name.replace('minecraft:', '').replace(/_/g, ' ')),
                datasets: [{
                    data: entries.map(([, count]) => count),
                    backgroundColor: chartColors.gradients,
                    borderWidth: 0
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        position: 'right',
                        labels: { color: '#94a3b8', boxWidth: 12, padding: 8 }
                    }
                }
            }
        });
    } catch (error) {
        console.error('Failed to load block types:', error);
    }
}

async function generateCustomChart() {
    const xAxis = document.getElementById('chartBuilderX')?.value || 'time';
    const yAxis = document.getElementById('chartBuilderY')?.value || 'count';
    const chartType = document.getElementById('chartBuilderType')?.value || 'bar';
    const filter = document.getElementById('chartBuilderFilter')?.value || '';
    const period = document.getElementById('analyticsPeriod')?.value || '7d';

    try {
        const data = await api.request(`/stats/custom?xAxis=${xAxis}&yAxis=${yAxis}&period=${period}&filter=${encodeURIComponent(filter)}`);

        const ctx = document.getElementById('customChart')?.getContext('2d');
        if (!ctx) return;

        if (customChart) customChart.destroy();

        customChart = new Chart(ctx, {
            type: chartType,
            data: {
                labels: data.map(d => d.label),
                datasets: [{
                    label: yAxis.replace(/_/g, ' '),
                    data: data.map(d => d.value),
                    backgroundColor: chartType === 'doughnut' ? chartColors.gradients : chartColors.primary,
                    borderColor: chartColors.primary,
                    borderWidth: chartType === 'line' ? 2 : 0,
                    fill: chartType === 'line',
                    tension: 0.4,
                    borderRadius: chartType === 'bar' ? 4 : 0
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { display: chartType === 'doughnut', labels: { color: '#94a3b8' } } },
                scales: chartType !== 'doughnut' ? {
                    x: { ticks: { color: '#64748b' }, grid: { display: false } },
                    y: { ticks: { color: '#64748b' }, grid: { color: 'rgba(255,255,255,0.05)' } }
                } : {}
            }
        });
    } catch (error) {
        console.error('Failed to generate custom chart:', error);
    }
}

async function loadAdminHotspots() {
    const userRole = localStorage.getItem('userRole');
    if (userRole !== 'admin') return;

    try {
        const data = await api.getHeatmap();

        const container = document.getElementById('hotspotsList');
        if (!container || !data || data.length === 0) {
            if (container) container.innerHTML = '<div class="text-slate-500 text-sm">Keine Hotspots gefunden</div>';
            return;
        }

        // Sort by count and take top 12
        const hotspots = data.sort((a, b) => b.count - a.count).slice(0, 12);

        container.innerHTML = hotspots.map(h => {
            const blockX = h.chunkX * 16 + 8;
            const blockZ = h.chunkZ * 16 + 8;
            const intensity = h.count > 200 ? 'high' : h.count > 50 ? 'medium' : 'low';
            const color = intensity === 'high' ? 'red' : intensity === 'medium' ? 'yellow' : 'cyan';

            return `
                <div class="p-3 rounded-lg bg-white/5 border-l-2 border-${color}-500">
                    <div class="flex justify-between items-center mb-1">
                        <span class="text-white font-medium">${h.world || 'world'}</span>
                        <span class="text-${color}-400 font-bold">${h.count}</span>
                    </div>
                    <div class="text-slate-400 text-sm font-mono">
                        X: ${blockX} | Z: ${blockZ}
                    </div>
                    <div class="text-xs text-slate-500 mt-1">Chunk: ${h.chunkX}, ${h.chunkZ}</div>
                </div>
            `;
        }).join('');
    } catch (error) {
        console.error('Failed to load admin hotspots:', error);
    }
}


function loadSection(sectionId) {
    switch (sectionId) {
        case 'dashboard':
            loadDashboardStats();
            break;
        case 'logs':
            searchBlockLogs();
            searchContainerLogs();
            break;
        case 'analytics':
            // Delay to allow section to become visible
            setTimeout(() => {
                initAnalytics();
            }, 100);
            break;
        case 'admin':
            loadSuspicious();
            loadUsers();
            break;
        case 'settings':
            loadSettings();
            break;
    }
}

// Load worlds for dropdowns
async function loadWorlds() {
    try {
        const worlds = await api.getWorlds();
        const selects = [
            document.getElementById('globalWorldFilter'),
            document.getElementById('heatmapWorldFilter')
        ];

        selects.forEach(select => {
            if (!select) return;
            while (select.options.length > 1) {
                select.remove(1);
            }
            worlds.forEach(world => {
                const option = document.createElement('option');
                option.value = world;
                option.textContent = world;
                select.appendChild(option);
            });
        });
    } catch (error) {
        console.error('Failed to load worlds:', error);
    }
}

// Format timestamp
function formatTime(timestamp) {
    const date = new Date(timestamp * 1000);
    return date.toLocaleString('de-DE', {
        day: '2-digit',
        month: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
    });
}

// Dashboard Stats
async function loadDashboardStats() {
    try {
        const stats = await api.getStats();

        document.getElementById('statBlocksPlaced').textContent = (stats.totalBlocksPlaced || 0).toLocaleString();
        document.getElementById('statBlocksBroken').textContent = (stats.totalBlocksBroken || 0).toLocaleString();
        document.getElementById('statItemsAdded').textContent = (stats.totalItemsAdded || 0).toLocaleString();
        document.getElementById('statUniquePlayers').textContent = (stats.uniquePlayers || 0).toLocaleString();

        // Top players
        const topPlayersList = document.getElementById('topPlayersList');
        topPlayersList.innerHTML = '';

        if (stats.topPlayers && stats.topPlayers.length > 0) {
            stats.topPlayers.forEach((player, index) => {
                const div = document.createElement('div');
                div.className = 'flex items-center justify-between p-3 rounded-xl hover:bg-white/5 transition-colors';
                div.innerHTML = `
                    <div class="flex items-center gap-3">
                        <div class="w-8 h-8 rounded-full bg-slate-700 flex items-center justify-center text-xs font-bold text-slate-400">${index + 1}</div>
                        <span class="text-white font-medium">${escapeHtml(player.playerName)}</span>
                    </div>
                    <span class="text-slate-400 text-sm">${player.actionCount.toLocaleString()} Aktionen</span>
                `;
                topPlayersList.appendChild(div);
            });
        } else {
            topPlayersList.innerHTML = '<div class="text-slate-500 text-sm p-3">Keine Daten verfügbar</div>';
        }

        // Load timeline chart
        await loadTimelineChart();
    } catch (error) {
        console.error('Failed to load dashboard stats:', error);
    }
}

async function loadTimelineChart() {
    try {
        const stats = await api.getTimelineStats();
        const ctx = document.getElementById('timelineChart').getContext('2d');

        if (timelineChart) {
            timelineChart.destroy();
        }

        timelineChart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: stats.map(e => e.timeSlot ? e.timeSlot.split(' ')[1] : ''),
                datasets: [
                    {
                        label: 'Blöcke',
                        data: stats.map(e => e.blockCount),
                        borderColor: '#00f0e8',
                        backgroundColor: 'rgba(0, 240, 232, 0.1)',
                        tension: 0.4,
                        fill: true,
                        borderWidth: 2
                    },
                    {
                        label: 'Container',
                        data: stats.map(e => e.containerCount),
                        borderColor: '#ffcc00',
                        backgroundColor: 'rgba(255, 204, 0, 0.1)',
                        tension: 0.4,
                        fill: true,
                        borderWidth: 2
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        labels: { color: '#94a3b8', font: { family: 'Inter' } }
                    }
                },
                scales: {
                    y: {
                        grid: { color: 'rgba(255, 255, 255, 0.05)' },
                        ticks: { color: '#64748b' }
                    },
                    x: {
                        grid: { color: 'rgba(255, 255, 255, 0.05)' },
                        ticks: { color: '#64748b' }
                    }
                }
            }
        });
    } catch (error) {
        console.error('Failed to load timeline:', error);
    }
}

// Block Logs
async function searchBlockLogs(page = 1) {
    currentBlockPage = page;
    const player = document.getElementById('blockPlayerFilter').value;
    const world = document.getElementById('globalWorldFilter').value;

    try {
        const logs = await api.getBlockLogs({ player, world, page, limit: 20 });
        const tbody = document.getElementById('blockLogsBody');
        tbody.innerHTML = '';

        if (logs.length === 0) {
            tbody.innerHTML = '<tr><td colspan="4" class="px-4 py-8 text-center text-slate-500">Keine Einträge gefunden</td></tr>';
            document.getElementById('blockPagination').innerHTML = '';
            return;
        }

        logs.forEach(log => {
            const actionClass = log.action === 0 ? 'text-red-400' : 'text-emerald-400';
            const actionText = log.action === 0 ? 'Zerstört' : 'Platziert';
            const tr = document.createElement('tr');
            tr.className = 'hover:bg-white/5 transition-colors';
            tr.innerHTML = `
                <td class="px-4 py-3 text-slate-400 text-xs font-mono">${formatTime(log.timestamp)}</td>
                <td class="px-4 py-3 text-white font-medium">${escapeHtml(log.playerName)}</td>
                <td class="px-4 py-3"><span class="${actionClass} text-xs font-medium">${actionText}</span></td>
                <td class="px-4 py-3 text-slate-300">${escapeHtml(log.blockType)}</td>
            `;
            tbody.appendChild(tr);
        });

        renderPagination('blockPagination', page, logs.length === 20, searchBlockLogs);
    } catch (error) {
        console.error('Failed to load block logs:', error);
    }
}

// Container Logs
async function searchContainerLogs(page = 1) {
    currentContainerPage = page;
    const player = document.getElementById('containerPlayerFilter').value;
    const world = document.getElementById('globalWorldFilter').value;

    try {
        const logs = await api.getContainerLogs({ player, world, page, limit: 20 });
        const tbody = document.getElementById('containerLogsBody');
        tbody.innerHTML = '';

        if (logs.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" class="px-4 py-8 text-center text-slate-500">Keine Einträge gefunden</td></tr>';
            document.getElementById('containerPagination').innerHTML = '';
            return;
        }

        logs.forEach(log => {
            const actionClass = log.action === 0 ? 'text-red-400' : 'text-emerald-400';
            const actionText = log.action === 0 ? 'Entfernt' : 'Hinzugefügt';
            const tr = document.createElement('tr');
            tr.className = 'hover:bg-white/5 transition-colors';
            tr.innerHTML = `
                <td class="px-4 py-3 text-slate-400 text-xs font-mono">${formatTime(log.timestamp)}</td>
                <td class="px-4 py-3 text-white font-medium">${escapeHtml(log.playerName)}</td>
                <td class="px-4 py-3"><span class="${actionClass} text-xs font-medium">${actionText}</span></td>
                <td class="px-4 py-3 text-slate-300">${escapeHtml(log.itemMaterial)}</td>
                <td class="px-4 py-3 text-slate-400">${log.itemAmount}</td>
            `;
            tbody.appendChild(tr);
        });

        renderPagination('containerPagination', page, logs.length === 20, searchContainerLogs);
    } catch (error) {
        console.error('Failed to load container logs:', error);
    }
}

// Heatmap / BlueMap
function initHeatmap() {
    // Only listener for resize to adjust iframe if needed
}

async function loadHeatmapData() {
    try {
        const data = await api.getHeatmapData();
        const worldFilter = document.getElementById('heatmapWorldFilter');
        const selectedWorld = worldFilter ? worldFilter.value : '';

        heatmapData = selectedWorld ? data.filter(d => d.world === selectedWorld) : data;

        // Update top regions list (Keep this as it's useful stats)
        const topRegionsList = document.getElementById('topRegionsList');
        if (topRegionsList) {
            topRegionsList.innerHTML = '';
            heatmapData.slice(0, 5).forEach(chunk => {
                const div = document.createElement('div');
                div.className = 'flex items-center justify-between p-3 rounded-xl bg-white/5 hover:bg-white/10 transition-colors cursor-pointer';
                div.innerHTML = `
                    <div class="flex items-center gap-3">
                        <div class="w-8 h-8 rounded-lg bg-primary/10 flex items-center justify-center text-primary">
                            <span class="material-symbols-outlined text-sm">location_on</span>
                        </div>
                        <div>
                            <span class="text-white text-sm font-medium">Chunk (${chunk.chunkX}, ${chunk.chunkZ})</span>
                            <span class="text-slate-500 text-xs block">${chunk.world}</span>
                        </div>
                    </div>
                    <div class="text-right">
                        <span class="text-white font-bold">${chunk.count}</span>
                        <span class="text-slate-500 text-xs block">Aktionen</span>
                    </div>
                `;
                topRegionsList.appendChild(div);
            });

            if (heatmapData.length === 0) {
                topRegionsList.innerHTML = '<div class="text-slate-500 text-sm p-3">Keine Daten verfügbar</div>';
            }
        }
    } catch (error) {
        console.error('Failed to load heatmap data:', error);
    }
}


// Export functions
async function exportBlockLogs() {
    const player = document.getElementById('blockPlayerFilter').value;
    const world = document.getElementById('globalWorldFilter').value;
    const params = new URLSearchParams();
    if (player) params.set('player', player);
    if (world) params.set('world', world);

    try {
        await api.download(`/export/blocks?${params.toString()}`, 'block_logs.csv');
    } catch (e) {
        alert('Export fehlgeschlagen: ' + e.message);
    }
}

async function exportContainerLogs() {
    const player = document.getElementById('containerPlayerFilter').value;
    const world = document.getElementById('globalWorldFilter').value;
    const params = new URLSearchParams();
    if (player) params.set('player', player);
    if (world) params.set('world', world);

    try {
        await api.download(`/export/containers?${params.toString()}`, 'container_logs.csv');
    } catch (e) {
        alert('Export fehlgeschlagen: ' + e.message);
    }
}

// Suspicious Players
async function loadSuspicious() {
    try {
        const data = await api.getSuspiciousPlayers();
        const tbody = document.getElementById('suspiciousBody');
        tbody.innerHTML = '';

        if (data.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" class="px-6 py-8 text-center text-slate-500">Keine verdächtigen Spieler gefunden.</td></tr>';
            return;
        }

        data.forEach(entry => {
            const ratio = ((entry.diamonds + entry.debris) / entry.totalBroken * 100).toFixed(1);
            const tr = document.createElement('tr');
            tr.className = 'hover:bg-white/5 transition-colors';
            tr.innerHTML = `
                <td class="px-6 py-4">
                    <div class="flex items-center gap-3">
                        <div class="w-8 h-8 rounded-lg bg-slate-700 flex items-center justify-center">
                            <span class="material-symbols-outlined text-slate-400 text-sm">person</span>
                        </div>
                        <span class="text-white font-medium">${escapeHtml(entry.playerName)}</span>
                    </div>
                </td>
                <td class="px-6 py-4 text-slate-300">${entry.totalBroken.toLocaleString()}</td>
                <td class="px-6 py-4 text-amber-400 font-medium">${entry.diamonds.toLocaleString()}</td>
                <td class="px-6 py-4 text-red-400 font-medium">${entry.debris.toLocaleString()}</td>
                <td class="px-6 py-4 text-right">
                    <span class="px-2.5 py-1 rounded-full text-xs font-bold bg-red-500 text-white">${ratio}%</span>
                </td>
            `;
            tbody.appendChild(tr);
        });
    } catch (error) {
        console.error('Failed to load suspicious players:', error);
    }
}

// Users (Admin)
async function loadUsers() {
    try {
        const users = await api.getUsers();
        const tbody = document.getElementById('usersBody');
        tbody.innerHTML = '';

        users.forEach(user => {
            const roleClass = user.role === 'admin' ? 'bg-red-500/20 text-red-300 border-red-500/30' : 'bg-primary/20 text-primary border-primary/30';
            const tr = document.createElement('tr');
            tr.className = 'hover:bg-white/5 transition-colors';
            tr.innerHTML = `
                <td class="px-6 py-4 text-white font-medium">${escapeHtml(user.username)}</td>
                <td class="px-6 py-4 text-slate-300">${escapeHtml(user.minecraftName)}</td>
                <td class="px-6 py-4">
                    <span class="px-2.5 py-0.5 rounded-full text-xs font-medium border ${roleClass}">${user.role}</span>
                </td>
                <td class="px-6 py-4 text-slate-400 text-sm">${formatTime(user.createdAt)}</td>
                <td class="px-6 py-4 text-right">
                    <button onclick="deleteUser(${user.id}, '${escapeHtml(user.username)}')" 
                            class="px-3 py-1.5 rounded-lg bg-red-500/10 text-red-400 text-xs font-medium hover:bg-red-500 hover:text-white transition-colors">
                        Löschen
                    </button>
                </td>
            `;
            tbody.appendChild(tr);
        });
    } catch (error) {
        console.error('Failed to load users:', error);
    }
}

async function deleteUser(userId, username) {
    if (!confirm(`Möchtest du den Benutzer "${username}" wirklich löschen?`)) {
        return;
    }

    try {
        await api.deleteUser(userId);
        loadUsers();
    } catch (error) {
        alert(error.message);
    }
}



// Pagination
function renderPagination(elementId, currentPage, hasMore, callback) {
    const container = document.getElementById(elementId);
    container.innerHTML = '';

    if (currentPage > 1) {
        const prevBtn = document.createElement('button');
        prevBtn.className = 'px-4 py-2 rounded-lg bg-white/5 hover:bg-white/10 text-white text-xs font-medium transition-colors';
        prevBtn.textContent = '← Zurück';
        prevBtn.onclick = () => callback(currentPage - 1);
        container.appendChild(prevBtn);
    }

    const pageBtn = document.createElement('button');
    pageBtn.className = 'px-4 py-2 rounded-lg bg-primary/20 text-primary text-xs font-medium';
    pageBtn.textContent = `Seite ${currentPage}`;
    container.appendChild(pageBtn);

    if (hasMore) {
        const nextBtn = document.createElement('button');
        nextBtn.className = 'px-4 py-2 rounded-lg bg-white/5 hover:bg-white/10 text-white text-xs font-medium transition-colors';
        nextBtn.textContent = 'Weiter →';
        nextBtn.onclick = () => callback(currentPage + 1);
        container.appendChild(nextBtn);
    }
}

// Utility
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function logout() {
    api.logout();
}

// Settings
async function loadSettings() {
    try {
        const settings = await api.getAdminSettings();

        // Populate form fields
        document.getElementById('settingsTimezone').value = settings.timezone || 'Europe/Berlin';
        document.getElementById('settingsServerIp').value = settings.serverIp || '';
        document.getElementById('settingsMapEnabled').checked = settings.mapEnabled;
        document.getElementById('settingsBluemapUrl').value = settings.bluemapUrl || '';
        document.getElementById('settingsMarkerInterval').value = settings.markerUpdateInterval || 5;

        // Display current port/host (read-only info)
        document.getElementById('settingsCurrentPort').textContent = settings.webPort || '-';
        document.getElementById('settingsCurrentHost').textContent = settings.webHost || '-';

        // Populate worlds dropdown for settings
        const worldSelect = document.getElementById('settingsDefaultWorld');
        const worlds = await api.getWorlds();
        if (worlds && worlds.length > 0) {
            worldSelect.innerHTML = worlds.map(w =>
                `<option value="${w}" ${w === settings.defaultWorld ? 'selected' : ''}>${w}</option>`
            ).join('');
        }
    } catch (error) {
        console.error('Failed to load settings:', error);
    }
}

async function saveSettings() {
    try {
        const settings = {
            timezone: document.getElementById('settingsTimezone').value,
            serverIp: document.getElementById('settingsServerIp').value,
            mapEnabled: document.getElementById('settingsMapEnabled').checked,
            bluemapUrl: document.getElementById('settingsBluemapUrl').value,
            markerUpdateInterval: parseInt(document.getElementById('settingsMarkerInterval').value) || 5,
            defaultWorld: document.getElementById('settingsDefaultWorld').value
        };

        const result = await api.saveAdminSettings(settings);
        alert(result.message || 'Einstellungen gespeichert!');
    } catch (error) {
        alert('Fehler beim Speichern: ' + error.message);
    }
}

// Settings form submit handler
document.addEventListener('DOMContentLoaded', () => {
    const settingsForm = document.getElementById('settingsForm');
    if (settingsForm) {
        settingsForm.addEventListener('submit', (e) => {
            e.preventDefault();
            saveSettings();
        });
    }
});
