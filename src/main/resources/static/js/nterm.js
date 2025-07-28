// 添加键盘事件监听
document.addEventListener('DOMContentLoaded', function() {
    const terminalInput = document.getElementById('terminal-input');
    
    if (terminalInput) {
        terminalInput.addEventListener('keydown', function(event) {
            if (event.key === 'Enter') {
                const command = terminalInput.value;
                if (command && window.terminalWebSocket && window.terminalWebSocket.readyState === WebSocket.OPEN) {
                    // 显示用户输入的命令
                    const terminalOutput = document.getElementById('terminal-output');
                    terminalOutput.innerHTML += '> ' + command + '\n';
                    terminalOutput.scrollTop = terminalOutput.scrollHeight;
                    
                    // 发送命令到后端
                    window.terminalWebSocket.send(command);
                    terminalInput.value = '';
                }
            }
        });
    }
});
