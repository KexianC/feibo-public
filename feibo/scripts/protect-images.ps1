# 图片文件保护脚本
# 用于检查和修复损坏的PNG图片文件

$imageFiles = @(
    @{ Path = "public\img\login_bg.png"; Url = "https://dd.feibointel.com/img/login_bg.png" },
    @{ Path = "public\img\loginbox_2.png"; Url = "https://dd.feibointel.com/img/loginbox_2.png" },
    @{ Path = "public\img\white_logo.png"; Url = "https://dd.feibointel.com/img/white_logo.png" }
)

$correctPngHeader = @(137, 80, 78, 71, 13, 10, 26, 10)

function Test-PngFile {
    param([string]$FilePath)
    
    if (-not (Test-Path $FilePath)) {
        return $false
    }
    
    try {
        $bytes = [System.IO.File]::ReadAllBytes($FilePath)[0..7]
        for ($i = 0; $i -lt 8; $i++) {
            if ($bytes[$i] -ne $correctPngHeader[$i]) {
                return $false
            }
        }
        return $true
    }
    catch {
        return $false
    }
}

function Repair-PngFile {
    param([string]$FilePath, [string]$Url)
    
    Write-Host "修复损坏的图片文件: $FilePath" -ForegroundColor Yellow
    
    # 移除只读属性
    if (Test-Path $FilePath) {
        Set-ItemProperty -Path $FilePath -Name IsReadOnly -Value $false
        Remove-Item $FilePath -Force
    }
    
    # 重新下载
    try {
        Invoke-WebRequest -Uri $Url -OutFile $FilePath
        # 设置为只读
        Set-ItemProperty -Path $FilePath -Name IsReadOnly -Value $true
        Write-Host "图片文件修复成功: $FilePath" -ForegroundColor Green
    }
    catch {
        Write-Host "图片文件修复失败: $FilePath - $($_.Exception.Message)" -ForegroundColor Red
    }
}

Write-Host "开始检查图片文件完整性..." -ForegroundColor Cyan

foreach ($image in $imageFiles) {
    $filePath = $image.Path
    $url = $image.Url
    
    if (-not (Test-PngFile $filePath)) {
        Write-Host "发现损坏的图片文件: $filePath" -ForegroundColor Red
        Repair-PngFile $filePath $url
    } else {
        Write-Host "图片文件正常: $filePath" -ForegroundColor Green
    }
}

Write-Host "图片文件检查完成!" -ForegroundColor Cyan