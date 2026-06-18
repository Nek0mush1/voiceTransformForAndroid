[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = "Stop"

function New-UnicodeString {
  param([int[]]$CodePoints)
  return -join ($CodePoints | ForEach-Object { [string][char]$_ })
}

$rawText = New-UnicodeString @(
  0x4eca, 0x5929, 0x4e0a, 0x5348, 0x4e0a, 0x4e86,
  0x4e24, 0x8282, 0x796d, 0x7956, 0x8bfe
)
$expectedText = New-UnicodeString @(
  0x4eca, 0x5929, 0x4e0a, 0x5348, 0x4e0a, 0x4e86,
  0x4e24, 0x8282, 0x8ba1, 0x7ec4, 0x8bfe
)
$expectedTerm = New-UnicodeString @(0x8ba1, 0x7ec4)

$body = @{
  user_id = "local_user"
  raw_text = $rawText
  app_context = "chat"
} | ConvertTo-Json -Compress

try {
  $request = [System.Net.WebRequest]::Create("http://127.0.0.1:8000/api/v1/correct-text")
  $request.Method = "POST"
  $request.ContentType = "application/json; charset=utf-8"

  $requestBytes = [System.Text.Encoding]::UTF8.GetBytes($body)
  $request.ContentLength = $requestBytes.Length
  $requestStream = $request.GetRequestStream()
  $requestStream.Write($requestBytes, 0, $requestBytes.Length)
  $requestStream.Close()

  $response = $request.GetResponse()
  $responseStream = $response.GetResponseStream()
  $reader = [System.IO.StreamReader]::new($responseStream, [System.Text.Encoding]::UTF8)
  $json = $reader.ReadToEnd()
  $data = $json | ConvertFrom-Json

  if ($data.corrected_text -ne $expectedText) {
    throw "Unexpected corrected_text: $($data.corrected_text)"
  }

  if (($data.matched_terms -join ",") -ne $expectedTerm) {
    throw "Unexpected matched_terms: $($data.matched_terms -join ',')"
  }

  $json
} finally {
  if ($reader) {
    $reader.Dispose()
  }
  if ($responseStream) {
    $responseStream.Dispose()
  }
  if ($response) {
    $response.Dispose()
  }
}
