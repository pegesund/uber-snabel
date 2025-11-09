// Global state
let currentSessionId = null;
let websocket = null;

// Initialize on page load
document.addEventListener('DOMContentLoaded', function() {
    checkStatus();
    loadSessions();
    loadMfes();
    setInterval(checkStatus, 10000); // Update status every 10 seconds
});

// Tab Management
function showTab(tabName) {
    // Update tab buttons
    document.querySelectorAll('.tab-button').forEach(btn => {
        btn.classList.remove('border-indigo-500', 'text-indigo-600');
        btn.classList.add('border-transparent', 'text-gray-500');
    });
    document.getElementById('tab-' + tabName).classList.add('border-indigo-500', 'text-indigo-600');
    document.getElementById('tab-' + tabName).classList.remove('border-transparent', 'text-gray-500');

    // Update content
    document.querySelectorAll('.tab-content').forEach(content => {
        content.classList.add('hidden');
    });
    document.getElementById('content-' + tabName).classList.remove('hidden');

    // Load data for specific tabs
    if (tabName === 'sessions') {
        loadSessions();
    } else if (tabName === 'config') {
        loadConfig();
    }
}

// Status Checking
async function checkStatus() {
    try {
        // Check frontend status
        const frontendResp = await fetch('/api/status/frontend');
        const frontend = await frontendResp.json();
        document.getElementById('frontend-status').innerHTML = frontend.running ?
            '<span class="text-green-600">Running</span>' :
            '<span class="text-red-600">Stopped</span>';

        // Check backend status
        const backendResp = await fetch('/api/status/backend');
        const backend = await backendResp.json();
        document.getElementById('backend-status').innerHTML = backend.running ?
            '<span class="text-green-600">Running</span>' :
            '<span class="text-red-600">Stopped</span>';

        // Update session count
        const sessionsResp = await fetch('/api/import/sessions');
        const sessions = await sessionsResp.json();
        document.getElementById('session-count').textContent = sessions.length;

    } catch (error) {
        console.error('Failed to check status:', error);
    }
}

// Load available MFEs
async function loadMfes() {
    try {
        const response = await fetch('/api/import/mfes');
        const mfes = await response.json();

        const select = document.getElementById('targetMfe');
        select.innerHTML = '<option value="">None - Use frontend root directory</option>';

        mfes.forEach(mfe => {
            const option = document.createElement('option');
            option.value = mfe.name;
            option.textContent = `${mfe.name} - ${mfe.description}`;
            select.appendChild(option);
        });

    } catch (error) {
        console.error('Failed to load MFEs:', error);
        document.getElementById('targetMfe').innerHTML = '<option value="">Failed to load MFEs</option>';
    }
}

// Session Management
async function createSession() {
    const description = document.getElementById('description').value;
    const instructions = document.getElementById('instructions').value;
    const targetMfe = document.getElementById('targetMfe').value;

    if (!description) {
        alert('Please enter a description');
        return;
    }

    try {
        const response = await fetch('/api/import/session', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ description, instructions, targetMfe })
        });

        const data = await response.json();
        currentSessionId = data.sessionId;

        // Show upload step
        document.getElementById('step-upload').classList.remove('hidden');

    } catch (error) {
        alert('Failed to create session: ' + error.message);
    }
}

async function uploadZip() {
    const fileInput = document.getElementById('zipFile');
    if (!fileInput.files[0]) {
        alert('Please select a zip file');
        return;
    }

    const formData = new FormData();
    formData.append('file', fileInput.files[0]);

    try {
        const response = await fetch(`/api/import/session/${currentSessionId}/upload`, {
            method: 'POST',
            body: formData
        });

        const data = await response.json();

        // Show analysis results
        const analysisDiv = document.getElementById('analysis-results');
        analysisDiv.innerHTML = `
            <strong>Analysis Complete:</strong><br>
            - Total files: ${data.analysis.totalFiles}<br>
            - TypeScript files: ${data.analysis.typescriptFiles}<br>
            - JavaScript files: ${data.analysis.javascriptFiles}<br>
            - Size: ${data.analysis.totalSizeMB.toFixed(2)} MB
        `;

        // Show start step
        document.getElementById('step-start').classList.remove('hidden');

    } catch (error) {
        alert('Failed to upload zip: ' + error.message);
    }
}

async function startWithoutUpload() {
    if (!currentSessionId) {
        alert('No active session. Please create a session first.');
        return;
    }

    try {
        const response = await fetch(`/api/import/session/${currentSessionId}/start`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({})
        });

        const data = await response.json();

        // Hide upload step, show monitor
        document.getElementById('step-upload').classList.add('hidden');
        document.getElementById('step-monitor').classList.remove('hidden');

        // Connect to WebSocket for logs
        connectWebSocket(currentSessionId);

        alert('Claude Code started on branch: ' + data.branchName + '\n\nYou can now send commands via the input field below.');

    } catch (error) {
        alert('Failed to start Claude Code: ' + error.message);
    }
}

async function startTransformation() {
    try {
        const response = await fetch(`/api/import/session/${currentSessionId}/start`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({})
        });

        const data = await response.json();

        // Show monitor step
        document.getElementById('step-monitor').classList.remove('hidden');

        // Connect to WebSocket for logs
        connectWebSocket(currentSessionId);

        alert('Transformation started on branch: ' + data.branchName);

    } catch (error) {
        alert('Failed to start transformation: ' + error.message);
    }
}

async function stopProcess() {
    try {
        await fetch(`/api/import/session/${currentSessionId}/stop`, {
            method: 'POST'
        });
        alert('Process stopped');
    } catch (error) {
        alert('Failed to stop process: ' + error.message);
    }
}

async function mergeBranch() {
    if (!confirm('Are you sure you want to merge this branch to main? This cannot be undone.')) {
        return;
    }

    try {
        const response = await fetch(`/api/import/session/${currentSessionId}/merge`, {
            method: 'POST'
        });

        const data = await response.json();
        alert('Branch merged successfully!');

        // Reload sessions
        loadSessions();

    } catch (error) {
        alert('Failed to merge branch: ' + error.message);
    }
}

async function loadSessions() {
    try {
        const response = await fetch('/api/import/sessions');
        const sessions = await response.json();

        const listDiv = document.getElementById('sessions-list');

        if (sessions.length === 0) {
            listDiv.innerHTML = '<p class="text-gray-500">No sessions yet</p>';
            return;
        }

        listDiv.innerHTML = sessions.map(session => `
            <div class="border rounded-lg p-4 hover:bg-gray-50">
                <div class="flex justify-between items-start">
                    <div>
                        <h3 class="font-semibold">${escapeHtml(session.description)}</h3>
                        <p class="text-sm text-gray-500">Session ID: ${session.sessionId}</p>
                        <p class="text-sm text-gray-500">Created: ${new Date(session.createdAt).toLocaleString()}</p>
                    </div>
                    <div class="flex items-center space-x-2">
                        <span class="px-2 py-1 text-xs rounded-full ${getStatusClass(session.status)}">
                            ${session.status}
                        </span>
                        ${session.merged ? '<span class="px-2 py-1 text-xs bg-purple-100 text-purple-800 rounded-full">MERGED</span>' : ''}
                    </div>
                </div>
                <div class="mt-2">
                    <button onclick="viewSession('${session.sessionId}')" class="text-sm text-indigo-600 hover:text-indigo-800">
                        View Details
                    </button>
                </div>
            </div>
        `).join('');

    } catch (error) {
        console.error('Failed to load sessions:', error);
    }
}

async function viewSession(sessionId) {
    try {
        const response = await fetch(`/api/import/session/${sessionId}`);
        const session = await response.json();

        currentSessionId = sessionId;

        // Switch to new import tab and populate
        showTab('new');

        // Show monitor if session is running or completed
        if (session.status !== 'CREATED') {
            document.getElementById('step-monitor').classList.remove('hidden');

            if (session.isRunning) {
                connectWebSocket(sessionId);
            }
        }

    } catch (error) {
        alert('Failed to load session: ' + error.message);
    }
}

async function loadConfig() {
    try {
        const response = await fetch('/api/status/config');
        const config = await response.json();

        const configDiv = document.getElementById('config-content');
        configDiv.innerHTML = `
            <div class="space-y-4">
                ${Object.entries(config).map(([key, value]) => `
                    <div>
                        <label class="block text-sm font-medium text-gray-700">${key}</label>
                        <input type="text" value="${escapeHtml(value)}" readonly
                               class="mt-1 block w-full rounded-md border-gray-300 bg-gray-50 shadow-sm sm:text-sm p-2 border cursor-not-allowed">
                    </div>
                `).join('')}
            </div>
        `;

    } catch (error) {
        console.error('Failed to load config:', error);
    }
}

// WebSocket for logs
function connectWebSocket(sessionId) {
    if (websocket) {
        websocket.close();
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const url = `${protocol}//${window.location.host}/ws/logs/${sessionId}`;

    websocket = new WebSocket(url);

    websocket.onopen = function() {
        console.log('WebSocket connected');
        addLog('INFO', 'Connected to log stream');
    };

    websocket.onmessage = function(event) {
        try {
            const log = JSON.parse(event.data);
            addLog(log.level, log.message);
        } catch (e) {
            addLog('INFO', event.data);
        }
    };

    websocket.onerror = function(error) {
        console.error('WebSocket error:', error);
        addLog('ERROR', 'WebSocket connection error');
    };

    websocket.onclose = function() {
        console.log('WebSocket closed');
        addLog('INFO', 'Disconnected from log stream');
    };
}

function addLog(level, message) {
    const logOutput = document.getElementById('log-output');
    const logEntry = document.createElement('div');
    logEntry.className = 'log-entry log-' + level.toLowerCase();

    const timestamp = new Date().toLocaleTimeString();
    logEntry.textContent = `[${timestamp}] [${level}] ${message}`;

    logOutput.appendChild(logEntry);
    logOutput.scrollTop = logOutput.scrollHeight;
}

// Utility functions
function getStatusClass(status) {
    const statusClasses = {
        'CREATED': 'bg-gray-100 text-gray-800',
        'UNPACKING': 'bg-blue-100 text-blue-800',
        'ANALYZING': 'bg-blue-100 text-blue-800',
        'TRANSFORMING': 'bg-yellow-100 text-yellow-800',
        'RUNNING': 'bg-yellow-100 text-yellow-800',
        'PAUSED': 'bg-orange-100 text-orange-800',
        'VALIDATING': 'bg-purple-100 text-purple-800',
        'COMPLETED': 'bg-green-100 text-green-800',
        'FAILED': 'bg-red-100 text-red-800',
        'MERGED': 'bg-purple-100 text-purple-800'
    };
    return statusClasses[status] || 'bg-gray-100 text-gray-800';
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Send command to Claude Code process
async function sendCommand() {
    const input = document.getElementById('command-input');
    const command = input.value.trim();

    if (!command) {
        alert('Please enter a command');
        return;
    }

    if (!currentSessionId) {
        alert('No active session');
        return;
    }

    try {
        const response = await fetch(`/api/import/session/${currentSessionId}/command`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ command })
        });

        if (!response.ok) {
            const error = await response.json();
            alert('Failed to send command: ' + error.error);
            return;
        }

        input.value = ''; // Clear input

    } catch (error) {
        alert('Failed to send command: ' + error.message);
    }
}

// Reset frontend to last commit
async function resetFrontend() {
    if (!confirm('Are you sure you want to reset the frontend to HEAD? This will discard all uncommitted changes!')) {
        return;
    }

    try {
        const response = await fetch('/api/git/reset-frontend', {
            method: 'POST'
        });

        const data = await response.json();

        if (response.ok) {
            alert('Frontend reset successfully!\n\n' + data.message);
        } else {
            alert('Failed to reset frontend: ' + data.error);
        }

    } catch (error) {
        alert('Failed to reset frontend: ' + error.message);
    }
}
