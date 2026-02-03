<div align="center">

# 🛡️ Guardian

**Block & Container Logging with Web Dashboard for Paper Servers**

[![Version](https://img.shields.io/badge/Version-2.0-brightgreen?style=for-the-badge)](https://github.com/Frxme/Guardian)
[![API](https://img.shields.io/badge/API-1.21+-blue?style=for-the-badge)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-17+-orange?style=for-the-badge)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-BSD%203--Clause-green?style=for-the-badge)](LICENSE)

*Track who did what, when. Now with a modern Web Dashboard.*

</div>

---

## ✨ Features

| Feature | Description |
|---------|-------------|
| 🌐 **Web Dashboard** | Modern, responsive admin panel for managing logs and analytics |
| 📊 **Analytics** | Action distribution charts and server activity insights |
| 📦 **Block Logging** | Track all block breaks and placements with player attribution |
| 📦 **Container Logging** | Monitor item additions and removals from chests, barrels, etc. |
| 🔍 **Inspector Mode** | Click blocks directly to view their history |
| 🔒 **User Authentication** | Secure login with JWT tokens and role-based access |
| ⚡ **Async Database** | Zero lag - all operations run in the background |
| 💾 **SQLite Storage** | No external database setup required |
| 🗄️ **Double Chest Support** | Correct handling of large container logging |

---

## 📥 Installation

```
1. 📁 Download the latest Guardian.jar from releases
2. 📂 Drop it into your server's plugins folder  
3. 🔄 Restart your server
4. ✅ Done! Database created automatically
5. 🌐 Access web dashboard at http://your-server-ip:7070
```

---

## 🌐 Web Dashboard

Access the dashboard at `http://your-server-ip:6746` after installation.

### Features
- 📈 Real-time analytics and charts
- 📋 Browse and filter block/container logs
- 👤 User management with role-based access (Admin/User)
- 🔒 Secure JWT authentication
- 📱 Mobile-responsive design

### First-Time Setup
1. Navigate to the dashboard URL
2. Create your admin account
3. Configure your preferences

---

## 💻 Commands

| Command | Description | Permission |
|:--------|:------------|:-----------|
| `/lookup [page]` | 🔍 View block history at targeted block | `guardian.lookup` |
| `/inspect` | 👁️ Toggle inspector mode | `guardian.inspect` |
| `/guardian [page]` | 📦 View container item history | `guardian.inspect` |

### 🔗 Aliases

| Main Command | Aliases |
|:-------------|:--------|
| `/lookup` | `/guard`, `/glookup` |
| `/inspect` | `/ginspect`, `/gi` |
| `/guardian` | `/gcont` |

---

## 🔐 Permissions

| Permission | Description | Default |
|:-----------|:------------|:--------|
| `guardian.lookup` | Access block history lookup | OP |
| `guardian.inspect` | Access inspector mode & container history | OP |

---

## 🔍 Inspector Mode

Toggle with `/inspect` for interactive lookups:

| Action | Result |
|:-------|:-------|
| **Left-click** any block | View block break/place history |
| **Right-click** a container | View item add/remove history |
| **Right-click** non-container | View block history |

> 💡 All interactions are cancelled in inspector mode to prevent accidents!

---

## 📋 Supported Versions

| Requirement | Version |
|:------------|:--------|
| Minecraft | 1.21 - 1.21.5 |
| Server | Paper, Purpur, or compatible forks |
| Java | 17+ |

---

<div align="center">

## 📜 License

[BSD 3-Clause License](LICENSE)

---

<div align="center">

## 🤝 Partner

<a href="https://emeraldhost.de/frxme">
  <img src="https://cdn.emeraldhost.de/branding/icon/icon.png" width="80" alt="Emerald Host Logo">
</a>

### Powered by EmeraldHost

*DDoS-Protection, NVMe Performance und 99.9% Uptime.* *Der Host meines Vertrauens für alle Development-Server.*

<a href="https://emeraldhost.de/frxme">
  <img src="https://img.shields.io/badge/Code-Frxme10-10b981?style=for-the-badge&logo=gift&logoColor=white&labelColor=0f172a" alt="Use Code Frxme10 for 10% off">
</a>

</div>

**Made with ❤️ by Frxme**

</div>


