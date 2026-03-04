/* ============================================================================ */
/* CORTEX - MASTER STYLESHEET CONFIGURATION */
/* ============================================================================ */
/*
 * This file orchestrates the modular CSS architecture.
 *
 * LOAD ORDER:
 * 1. Core styles (theme-independent structure)
 * 2. Theme-specific styles (dark-purple, dark-green, or light)
 *
 * FOLDER STRUCTURE:
 * styles/
 * ├── core/                 (Theme-independent structure and layout)
 * │   ├── _base.css         (Typography, sizing constants)
 * │   ├── _layout.css       (Container and layout structures)
 * │   ├── _buttons.css      (Button structure without colors)
 * │   ├── _inputs.css       (Input field structure without colors)
 * │   ├── _bubbles.css      (Message bubble structure without colors)
 * │   └── _components.css   (Miscellaneous UI components structure)
 * │
 * └── themes/               (Theme-specific colors and overrides)
 *     ├── dark-purple/      (Original purple theme)
 *     │   ├── _tokens.css   (Purple color variables)
 *     │   └── theme.css     (Complete purple theme with light mode)
 *     │
 *     ├── dark-green/       (Emerald green theme)
 *     │   ├── _tokens.css   (Green color variables)
 *     │   └── theme.css     (Complete green theme with light mode)
 *     │
 *     └── light/            (Light universal theme)
 *         ├── _tokens.css   (Light mode color variables)
 *         └── theme.css     (Light theme complete styles)
 *
 * USAGE IN JAVA:
 *
 * // Load Purple Dark Theme (default)
 * scene.getStylesheets().add(getClass().getResource("/styles/core/_base.css").toExternalForm());
 * scene.getStylesheets().add(getClass().getResource("/styles/core/_layout.css").toExternalForm());
 * scene.getStylesheets().add(getClass().getResource("/styles/core/_buttons.css").toExternalForm());
 * scene.getStylesheets().add(getClass().getResource("/styles/core/_inputs.css").toExternalForm());
 * scene.getStylesheets().add(getClass().getResource("/styles/core/_bubbles.css").toExternalForm());
 * scene.getStylesheets().add(getClass().getResource("/styles/core/_components.css").toExternalForm());
 * scene.getStylesheets().add(getClass().getResource("/styles/themes/dark-purple/theme.css").toExternalForm());
 *
 * // Switch to Green Dark Theme
 * scene.getStylesheets().clear();
 * // ... reload all core stylesheets ...
 * scene.getStylesheets().add(getClass().getResource("/styles/themes/dark-green/theme.css").toExternalForm());
 *
 * // Switch to Light Theme
 * scene.getStylesheets().clear();
 * // ... reload all core stylesheets ...
 * scene.getStylesheets().add(getClass().getResource("/styles/themes/light/theme.css").toExternalForm());
 *
 * MODULAR DESIGN BENEFITS:
 *
 * 1. SEPARATION OF CONCERNS
 *    - Core files define structure, layout, and sizing
 *    - Theme files define colors, gradients, and visual effects
 *    - Easy to maintain both independently
 *
 * 2. THEME CONSISTENCY
 *    - Each theme defines all color variations in _tokens.css
 *    - Light mode overrides are scoped to each theme
 *    - Colors propagate consistently across all components
 *
 * 3. REUSABILITY
 *    - Core styles can be reused with any theme
 *    - New themes only need to override color properties
 *    - No duplication of structure or layout code
 *
 * 4. MAINTAINABILITY
 *    - Smaller, focused files are easier to understand
 *    - Changes to structure don't affect theme files
 *    - Theme colors isolated in dedicated files
 *
 * 5. SCALABILITY
 *    - Adding new themes is as simple as creating theme/ subdirectory
 *    - No need to modify existing files
 *    - Clear naming conventions for consistency
 *
 * FILE NAMING CONVENTIONS:
 *
 * _prefix.css     — Internal/private stylesheet (underscore prefix)
 * theme.css       — Complete theme with all colors and overrides
 * _tokens.css     — Color variables and design tokens (for reference)
 *
 * CLASS NAMING CONVENTIONS (in use):
 *
 * Layout           Classes: .app-root, .sidebar, .chat-root, .chat-container
 * Components       Classes: .sidebar-search, .theme-button, .send-icon-button
 * Messaging        Classes: .bubble, .user-bubble, .bot-bubble, .messages-box
 * Code/Content     Classes: .code-panel, .code-badge, .code-text, .copy-button
 * Text Variations  Classes: .label, .label-muted, .typing-label, .chat-title
 * Modifiers        Classes: :hover, :pressed, :focused, .light-mode
 *
 * COMPATIBILITY:
 *
 * - All styles use JavaFX CSS syntax (compatible with JavaFX 8+)
 * - Tested with JavaFX 11, 16, and later versions
 * - No external dependencies or CSS preprocessors required
 * - Pure CSS with gradients, shadows, and effects
 *
 * ============================================================================ */
