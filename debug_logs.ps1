# AndroGPT Debug Log Monitor with Crash Detection
# Filters for relevant tags and monitors for app crashes

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "AndroGPT Debug Log Monitor" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Monitoring logs for:" -ForegroundColor Yellow
Write-Host ""
Write-Host "  Inference & Chat:" -ForegroundColor Magenta
Write-Host "    - ChatViewModel" -ForegroundColor Green
Write-Host "    - SendMessageUseCase" -ForegroundColor Green
Write-Host "    - LlamaEngine" -ForegroundColor Green
Write-Host "    - InferenceRepositoryImpl" -ForegroundColor Green
Write-Host ""
Write-Host "  Model Downloads:" -ForegroundColor Magenta
Write-Host "    - ModelDownloadRepo" -ForegroundColor Green
Write-Host "    - ModelDownloadViewModel" -ForegroundColor Green
Write-Host "    - ModelDownloadService" -ForegroundColor Green
Write-Host "    - DownloadNotificationManager" -ForegroundColor Green
Write-Host ""
Write-Host "  Model Management:" -ForegroundColor Magenta
Write-Host "    - ModelRepository" -ForegroundColor Green
Write-Host "    - ModelManager" -ForegroundColor Green
Write-Host "    - ModelsViewModel" -ForegroundColor Green
Write-Host ""
Write-Host "  Crash Detection:" -ForegroundColor Magenta
Write-Host "    - AndroidRuntime (Fatal exceptions)" -ForegroundColor Red
Write-Host "    - DEBUG (Native crashes)" -ForegroundColor Red
Write-Host ""
Write-Host "  Log Filtering:" -ForegroundColor Magenta
Write-Host "    - Hiding repetitive state updates" -ForegroundColor Yellow
Write-Host "    - Truncating long responses to 80 chars" -ForegroundColor Yellow
Write-Host "    - Filtering verbose token callbacks" -ForegroundColor Yellow
Write-Host ""
Write-Host "Try the following actions:" -ForegroundColor Yellow
Write-Host "  • Send a message in chat" -ForegroundColor White
Write-Host "  • Download a model" -ForegroundColor White
Write-Host "  • Pause/Resume download" -ForegroundColor White
Write-Host "  • Load a model" -ForegroundColor White
Write-Host ""
Write-Host "Press Ctrl+C to stop monitoring" -ForegroundColor Yellow
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

# Enhanced tag list including download monitoring and crash detection
& $adb logcat -s `
    "ChatViewModel:*" `
    "SendMessageUseCase:*" `
    "LlamaEngine:*" `
    "InferenceRepositoryImpl:*" `
    "ModelDownloadRepo:*" `
    "ModelDownloadViewModel:*" `
    "ModelDownloadService:*" `
    "DownloadNotificationManager:*" `
    "ModelRepository:*" `
    "ModelManager:*" `
    "ModelsViewModel:*" `
    "AndroidRuntime:*" `
    "DEBUG:*" | ForEach-Object {
    $line = $_
    
    # Detect crashes and highlight them
    if ($line -match "FATAL EXCEPTION" -or $line -match "AndroidRuntime.*FATAL") {
        Write-Host ""
        Write-Host "========================================" -ForegroundColor Red
        Write-Host "!!! APP CRASH DETECTED !!!" -ForegroundColor Red -BackgroundColor Yellow
        Write-Host "========================================" -ForegroundColor Red
        Write-Host $line -ForegroundColor Red
        return
    }
    
    if ($line -match "Fatal signal" -or $line -match "\*\*\* \*\*\* \*\*\*" -or $line -match "backtrace:") {
        Write-Host ""
        Write-Host "========================================" -ForegroundColor Red
        Write-Host "!!! NATIVE CRASH DETECTED !!!" -ForegroundColor Red -BackgroundColor Yellow
        Write-Host "========================================" -ForegroundColor Red
        Write-Host $line -ForegroundColor Red
        return
    }
    
    # Filter out repetitive/verbose logs
    if ($line -match "Keeping state as is:" -or 
        $line -match "Token callback: '\S{1,3}'" -or
        $line -match "Collected state #\d+: Generating" -or
        $line -match "Generating - cleaned text length:") {
        return
    }
    
    # Shorten very long text in logs (for currentText, Generated text, etc.)
    if ($line -match "currentText=.{80,}" -or $line -match "text=.{80,}" -or $line -match "response \(\d+ chars\):") {
        # Extract the prefix before the long text
        if ($line -match "^(.+?(?:currentText|text|response \(\d+ chars\))[:=])(.+)$") {
            $prefix = $matches[1]
            $content = $matches[2]
            
            # Truncate content to 80 characters
            if ($content.Length -gt 80) {
                $truncated = $content.Substring(0, 80) + "... [truncated]"
                $line = $prefix + $truncated
            }
        }
    }
    
    # Shorten individual token logs
    if ($line -match "Token callback:" -or $line -match "Streaming token:") {
        # Only show longer tokens (5+ characters) to reduce noise
        if ($line -match "Token callback: '.{5,}'") {
            $line = $line -replace "(Token callback: '.{20}).*'", '$1...'
        } else {
            return  # Skip short tokens
        }
    }
    
    # Color code based on log level
    if ($line -match " E ") {
        Write-Host $line -ForegroundColor Red
    }
    elseif ($line -match " W ") {
        Write-Host $line -ForegroundColor Yellow
    }
    elseif ($line -match " I ") {
        Write-Host $line -ForegroundColor Cyan
    }
    elseif ($line -match " D ") {
        # Highlight important debug messages
        if ($line -match "START|END|FATAL|ERROR|Loading|Complete|Generation") {
            Write-Host $line -ForegroundColor White
        } else {
            Write-Host $line -ForegroundColor Gray
        }
    }
    elseif ($line -match " V ") {
        # Show verbose logs in dark gray (very subtle)
        Write-Host $line -ForegroundColor DarkGray
    }
    else {
        Write-Host $line
    }
}
