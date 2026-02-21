# Input System Configuration

This folder contains the Input Actions asset for FlyAgain.

## FlyAgainInputActions.inputactions

The main Input Actions asset with two action maps:

### Gameplay Map
- **Move** (WASD): Character movement
- **Jump** (Space): Jump action
- **Attack** (Left Mouse Button): Primary attack
- **Interact** (E): Interact with NPCs/objects
- **OpenInventory** (I): Toggle inventory
- **OpenMenu** (Escape): Open game menu

### UI Map
- **Navigate** (Arrow Keys): Navigate UI elements
- **Submit** (Enter): Confirm/submit UI actions
- **Cancel** (Escape): Cancel/back in UI

## Setup Instructions

1. Select `FlyAgainInputActions.inputactions` in the Project window
2. In the Inspector, enable **"Generate C# Class"**
3. Set the **C# Class Name** to `FlyAgainInputActions`
4. Set the **C# Class Namespace** to `FlyAgain.Input`
5. Set the **C# Class File** path to `Assets/Scripts/Input/FlyAgainInputActions.cs`
6. Click **Apply**

Unity will generate a type-safe C# wrapper class that you can use in your scripts.

## Usage Example

```csharp
using FlyAgain.Input;
using UnityEngine;
using UnityEngine.InputSystem;

public class PlayerController : MonoBehaviour
{
    private FlyAgainInputActions _inputActions;

    private void Awake()
    {
        _inputActions = new FlyAgainInputActions();
    }

    private void OnEnable()
    {
        _inputActions.Gameplay.Enable();
        _inputActions.Gameplay.Move.performed += OnMove;
        _inputActions.Gameplay.Jump.performed += OnJump;
    }

    private void OnDisable()
    {
        _inputActions.Gameplay.Disable();
        _inputActions.Gameplay.Move.performed -= OnMove;
        _inputActions.Gameplay.Jump.performed -= OnJump;
    }

    private void OnMove(InputAction.CallbackContext context)
    {
        Vector2 moveInput = context.ReadValue<Vector2>();
        // Handle movement
    }

    private void OnJump(InputAction.CallbackContext context)
    {
        // Handle jump
    }
}
```

## EventSystem Configuration

Make sure your EventSystem uses the **Input System UI Input Module** instead of the legacy Standalone Input Module to work with the new Input System.
