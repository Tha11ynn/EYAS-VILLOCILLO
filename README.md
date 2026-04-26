# Eyas-Villocillo

## Game Title & Description

**Eyas-Villocillo** is a dynamic 2D platformer game developed in Java using Swing. The game challenges players to navigate through increasingly difficult levels filled with various platform types, hazards, and obstacles. Test your reflexes, timing, and problem-solving skills as you make your way through five unique levels, from the beginner-friendly Tutorial to the challenging final level.

### Features:
- **5 Unique Levels**: Tutorial, Getting There, Troll Central, Almost There, and The Finale
- **Dynamic Physics Engine**: Realistic gravity, momentum, and collision detection
- **Platform Variety**: 
  - Standard platforms
  - Bouncy platforms (launch you high)
  - Fake platforms (crumble on contact)
  - Invisible platforms (sparkle faintly)
  - Moving platforms
- **Hazard System**: Spikes, chasing obstacles, and environmental challenges
- **Smooth Animations**: Death animations, screen shake effects, and visual feedback
- **Level Progression**: Unlock new levels as you complete challenges
- **Pause & Resume**: Full game pause functionality with settings

---

## Setup Instructions

### Requirements:
- Java Development Kit (JDK) 8 or higher
- Java Runtime Environment (JRE)

### How to Run:

1. **Navigate to the project directory:**
   ```bash
   cd EYAS-VILLOCILLO
   ```

2. **Compile the Java file:**
   ```bash
   javac platform.java
   ```

3. **Run the game:**
   ```bash
   java platform
   ```

4. **The game window will launch automatically** with a 900x550 pixel display.

### Alternative (Using an IDE):
- Open `platform.java` in an IDE like IntelliJ IDEA, Eclipse, or NetBeans
- Click **Run** or press the run button to start the game

---

## List of Controls

### Main Menu
- **↑ / ↓ (Arrow Keys)**: Navigate menu options
- **ENTER**: Confirm selection

### In-Game Controls
- **A / ← (Left Arrow)**: Move left
- **D / → (Right Arrow)**: Move right
- **W / SPACE / ↑ (Up Arrow)**: Jump
- **R**: Restart the current level
- **ESC**: Pause the game
- **ENTER / SPACE**: Confirm/Resume from pause menu

### Pause Menu
- **↑ / ↓ (Arrow Keys)**: Navigate pause options
- **ENTER / SPACE**: Confirm selection
- **ESC**: Resume game

---

## Screenshots of the Final Build

### Main Menu
![Main Menu](./screenshots/main_menu.png)
*The welcoming main menu with options to Start Game, Level Select, or Quit*

### Level Select
![Level Select](./screenshots/level_select.png)
*Choose your level: Tutorial through The Finale*

### Gameplay - Tutorial Level
![Tutorial Level](./screenshots/tutorial_level.png)
*The beginner-friendly first level to learn the basics*

### Gameplay - Advanced Level
![Advanced Level](./screenshots/advanced_level.png)
*Later levels feature complex platform arrangements and hazards*

### Death Animation
![Death Animation](./screenshots/death_animation.png)
*Stylized particle effects when the player falls*

### Pause Menu
![Pause Menu](./screenshots/pause_menu.png)
*In-game pause menu with resume, restart, and settings options*

### Level Complete
![Level Complete](./screenshots/level_complete.png)
*Victory screen after successfully completing a level*

---

## Game Tips

- **Use Coyote Time**: You can still jump briefly after leaving a platform edge
- **Jump Buffering**: Press jump before landing to execute the jump immediately upon contact
- **Watch the Colors**: Different platform colors indicate different properties:
  - Red sparkles = Invisible (be careful!)
  - Cyan with ↑↑ = Bouncy (gains extra height)
  - Red crumbled look = Fake (will break when you land)
  - Red trail effect = Chasing hazards (run!)
- **Plan Your Route**: Study the level layout before making risky jumps
- **Collect Everything**: Each level completion adds to your score

---

## Technical Details

### Game Architecture:
- **Window**: 900x550 pixels
- **Game Loop**: Timer-based update system (60 FPS target)
- **Rendering**: Java Swing Graphics2D with custom drawing
- **Physics**: Custom gravity and collision detection system

### Key Classes:
- `Platform`: Represents platforms with various properties
- `Spike`: Hazard objects with optional chase behavior
- `TutorialCard`: In-game tutorial system
- `BodyPart`: Death animation particle system

---

## Credits

Developed by: EYAS-VILLOCILLO Team
Built with: Java & Swing
Year: 2026

---

## License

This is a student project. Feel free to use and modify for educational purposes.