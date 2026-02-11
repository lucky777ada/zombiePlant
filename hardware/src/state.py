import asyncio

# Global lock for hardware exclusivity
system_lock = asyncio.Lock()
