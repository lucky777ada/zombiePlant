import asyncio
from src.actuators.pumps import pump_controller

async def main():
    print("Testing FloraGro dispense via controller...")
    try:
        await pump_controller.dispense("flora_gro", 5)
        print("Dispense complete.")
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    asyncio.run(main())
