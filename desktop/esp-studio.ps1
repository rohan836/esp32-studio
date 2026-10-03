param(
  [Parameter(ValueFromRemainingArguments = $true)]
  [string[]] $ArgsList
)
py "$PSScriptRoot/esp_studio.py" @ArgsList
