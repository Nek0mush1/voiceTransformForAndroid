$body = @{
  user_id = "local_user"
  raw_text = "今天上午上了两节祭祖课"
  app_context = "chat"
} | ConvertTo-Json -Compress

Invoke-RestMethod `
  -Method Post `
  -Uri "http://127.0.0.1:8000/api/v1/correct-text" `
  -ContentType "application/json; charset=utf-8" `
  -Body $body |
  ConvertTo-Json -Compress
