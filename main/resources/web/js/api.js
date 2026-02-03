// Guardian API Client

const API_BASE = '/api';

class GuardianAPI {
    constructor() {
        this.token = localStorage.getItem('guardian_token');
    }

    setToken(token) {
        this.token = token;
        localStorage.setItem('guardian_token', token);
    }

    clearToken() {
        this.token = null;
        localStorage.removeItem('guardian_token');
        localStorage.removeItem('guardian_user');
    }

    setUser(user) {
        localStorage.setItem('guardian_user', JSON.stringify(user));
    }

    getUser() {
        const user = localStorage.getItem('guardian_user');
        return user ? JSON.parse(user) : null;
    }

    isLoggedIn() {
        return !!this.token;
    }

    async request(endpoint, options = {}, returnBlob = false) {
        const url = `${API_BASE}${endpoint}`;
        const headers = {
            'Content-Type': 'application/json',
            ...options.headers
        };

        if (this.token) {
            headers['Authorization'] = `Bearer ${this.token}`;
        }

        const response = await fetch(url, {
            ...options,
            headers
        });

        if (returnBlob) {
            if (!response.ok) throw new Error('Download fehlerhaft');
            return await response.blob();
        }

        const data = await response.json();

        if (!response.ok) {
            // Handle 401 Unauthorized - redirect to login
            if (response.status === 401) {
                this.clearToken();
                window.location.href = '/index.html';
                throw new Error('Sitzung abgelaufen. Bitte erneut anmelden.');
            }
            throw new Error(data.error || 'Ein Fehler ist aufgetreten');
        }

        return data;
    }

    async download(endpoint, filename) {
        const blob = await this.request(endpoint, {}, true);
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        a.remove();
        window.URL.revokeObjectURL(url);
    }

    // Auth
    async login(username, password) {
        const data = await this.request('/login', {
            method: 'POST',
            body: JSON.stringify({ username, password })
        });
        this.setToken(data.token);
        this.setUser({
            username: data.username,
            role: data.role,
            minecraftName: data.minecraftName
        });
        return data;
    }

    async register(code, username, password) {
        const data = await this.request('/register', {
            method: 'POST',
            body: JSON.stringify({ code, username, password })
        });
        this.setToken(data.token);
        this.setUser({
            username: data.username,
            role: data.role,
            minecraftName: data.minecraftName
        });
        return data;
    }

    async getMe() {
        return await this.request('/me');
    }

    // Logs
    async getBlockLogs(params = {}) {
        const queryParams = new URLSearchParams();
        if (params.player) queryParams.set('player', params.player);
        if (params.world) queryParams.set('world', params.world);
        if (params.from) queryParams.set('from', params.from);
        if (params.to) queryParams.set('to', params.to);
        if (params.page) queryParams.set('page', params.page);
        if (params.limit) queryParams.set('limit', params.limit);

        return await this.request(`/logs/blocks?${queryParams.toString()}`);
    }

    async getContainerLogs(params = {}) {
        const queryParams = new URLSearchParams();
        if (params.player) queryParams.set('player', params.player);
        if (params.world) queryParams.set('world', params.world);
        if (params.from) queryParams.set('from', params.from);
        if (params.to) queryParams.set('to', params.to);
        if (params.page) queryParams.set('page', params.page);
        if (params.limit) queryParams.set('limit', params.limit);

        return await this.request(`/logs/containers?${queryParams.toString()}`);
    }

    // Admin
    async getStats() {
        return await this.request('/admin/stats');
    }

    async getTimelineStats(from, to) {
        const queryParams = new URLSearchParams();
        if (from) queryParams.set('from', from);
        if (to) queryParams.set('to', to);
        return await this.request(`/stats/timeline?${queryParams.toString()}`);
    }

    async getHeatmapData(since) {
        const queryParams = new URLSearchParams();
        if (since) queryParams.set('since', since);
        return await this.request(`/admin/heatmap?${queryParams.toString()}`);
    }

    async getSuspiciousPlayers(since) {
        const queryParams = new URLSearchParams();
        if (since) queryParams.set('since', since);
        return await this.request(`/admin/suspicious?${queryParams.toString()}`);
    }

    async getUsers() {
        return await this.request('/admin/users');
    }

    async getWorlds() {
        return await this.request('/worlds');
    }

    async deleteUser(userId) {
        return await this.request(`/admin/users/${userId}`, {
            method: 'DELETE'
        });
    }

    async updateDiscordUsername(discordUsername) {
        return await this.request('/me/discord', {
            method: 'PUT',
            body: JSON.stringify({ discordUsername })
        });
    }

    async getConfig() {
        return await this.request('/config');
    }

    async logout() {
        this.clearToken();
        window.location.href = '/';
    }

    // Admin Settings
    async getAdminSettings() {
        return await this.request('/admin/settings');
    }

    async saveAdminSettings(settings) {
        return await this.request('/admin/settings', {
            method: 'POST',
            body: JSON.stringify(settings)
        });
    }

    // Heatmap data for map overlay
    async getHeatmap(world = 'world') {
        return await this.request(`/stats/heatmap?world=${encodeURIComponent(world)}`);
    }
}

const api = new GuardianAPI();
