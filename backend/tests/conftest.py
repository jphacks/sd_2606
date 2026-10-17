import os

# テストでは Claude API を呼ばない
os.environ["LLM_ENABLED"] = "0"
