class ScreenViewer {
    constructor() {
        this.socket = null;
        this.screenView = document.getElementById('screenView');
        this.statusElement = document.getElementById('status');
        this.statusText = document.getElementById('statusText');
        this.errorElement = document.getElementById('error');
        this.errorMessage = document.getElementById('errorMessage');
        this.frameCountElement = document.getElementById('frameCount');
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 5;
        this.frameCount = 0;
        this.lastFrameTime = Date.now();

        this.connect();
        this.startFrameRateCounter();
    }

    startFrameRateCounter() {
        setInterval(() => {
            const now = Date.now();
            const timeDiff = now - this.lastFrameTime;
            if (timeDiff > 5000) {
                this.updateStatus('No frames received', 'disconnected');
            }
        }, 1000);
    }

    connect() {
        try {
            const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
            const wsUrl = `${protocol}//${window.location.host}/capture`;

            this.socket = new WebSocket(wsUrl);

            this.socket.onopen = () => {
                console.log('Connected to screen capture server');
                this.updateStatus('Connected', 'connected');
                this.hideError();
                this.reconnectAttempts = 0;
            };

            this.socket.onmessage = (event) => {
                try {
                    const message = JSON.parse(event.data);
                    this.handleMessage(message);
                } catch (error) {
                    console.error('Failed to parse message:', error);
                }
            };

            this.socket.onclose = () => {
                console.log('Disconnected from screen capture server');
                this.updateStatus('Disconnected', 'disconnected');
                this.attemptReconnect();
            };

            this.socket.onerror = (error) => {
                console.error('WebSocket error:', error);
                this.updateStatus('Connection Error', 'disconnected');
                this.showError('Failed to connect to screen sharing server. Please check your connection.');
            };

        } catch (error) {
            console.error('Failed to create WebSocket connection:', error);
            this.showError('Unable to establish connection to screen sharing server.');
        }
    }

    handleMessage(message) {
        switch (message.type) {
            case 'connected':
                console.log('Server acknowledged connection');
                break;

            case 'frame':
                this.screenView.src = message.data;
                this.frameCount++;
                this.frameCountElement.textContent = this.frameCount;
                this.lastFrameTime = Date.now();
                this.updateStatus('Streaming', 'connected');
                break;

            default:
                console.log('Unknown message type:', message.type);
        }
    }

    updateStatus(text, className) {
        this.statusText.textContent = text;
        this.statusElement.className = `status ${className}`;
    }

    showError(message) {
        this.errorMessage.textContent = message;
        this.errorElement.style.display = 'block';
    }

    hideError() {
        this.errorElement.style.display = 'none';
    }

    attemptReconnect() {
        if (this.reconnectAttempts < this.maxReconnectAttempts) {
            this.reconnectAttempts++;
            const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 30000);

            this.updateStatus(
                `Reconnecting in ${Math.ceil(delay / 1000)}s... (${this.reconnectAttempts}/${this.maxReconnectAttempts})`,
                'connecting'
            );

            setTimeout(() => {
                this.updateStatus('Reconnecting...', 'connecting');
                this.connect();
            }, delay);
        } else {
            this.showError('Failed to reconnect after multiple attempts. Please refresh the page to try again.');
        }
    }
}

document.addEventListener('DOMContentLoaded', () => {
    new ScreenViewer();
});
