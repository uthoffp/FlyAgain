using System;
using System.Collections.Generic;
using FlyAgain.Proto;
using FlyAgain.UI.Core;
using UnityEngine;
using UnityEngine.UI;
using UnityEngine.InputSystem;

namespace FlyAgain.UI.Screens
{
    /// <summary>
    /// Character selection screen that displays up to 3 characters.
    /// Allows selecting a character or creating a new one (if < 3 characters exist).
    /// </summary>
    public class CharacterSelectScreen : BaseScreen
    {
        private const float BackgroundDarken = 0.7f;
        private const int MaxCharacters = 3;

        private FlyAgain.Proto.CharacterInfo[] _characters;
        private List<Button> _characterButtons = new List<Button>();
        private Button _createButton;
        private Button _logoutButton;
        private Text _errorText;
        private int _selectedIndex = 0;

        public event Action<long> OnCharacterSelected;         // character_id
        public event Action OnCreateCharacterRequested;
        public event Action OnLogoutRequested;

        protected override void OnBuild()
        {
            // Background with same style as LoginScreen
            Sprite backgroundSprite = Resources.Load<Sprite>("UI/login-background");
            RectTransform overlay;
            if (backgroundSprite != null)
            {
                Color tint = new Color(BackgroundDarken, BackgroundDarken, BackgroundDarken, 1f);
                overlay = UIFactory.CreateImagePanel(RootTransform, "Background", backgroundSprite, tint);
            }
            else
            {
                overlay = UIFactory.CreatePanel(RootTransform, "Overlay", UITheme.ScreenOverlay);
            }
            UIFactory.StretchToParent(overlay);

            // Window with semi-transparent background
            var window = BaseWindow.Create(RootTransform, LocalizationManager.Get("character.select.title"), 500, 600);
            var windowImage = window.GetComponent<Image>();
            if (windowImage != null)
            {
                Color windowColor = windowImage.color;
                windowColor.a = 0.75f;
                windowImage.color = windowColor;
            }

            // Character list container (will be populated in SetCharacterList)
            var characterListContainer = new GameObject("CharacterListContainer");
            characterListContainer.transform.SetParent(window.ContentArea, false);
            var containerRect = characterListContainer.AddComponent<RectTransform>();
            UIFactory.AddVerticalLayout(characterListContainer, 8);
            var containerLayout = characterListContainer.AddComponent<LayoutElement>();
            containerLayout.flexibleHeight = 1;

            // Flexible spacer
            UIFactory.CreateSpacer(window.ContentArea, flexibleHeight: 0.5f);

            // Create Character button
            _createButton = UIFactory.CreateButton(window.ContentArea, LocalizationManager.Get("character.select.create"));
            _createButton.onClick.AddListener(HandleCreateCharacter);

            UIFactory.CreateSpacer(window.ContentArea, 8);

            // Logout button
            _logoutButton = UIFactory.CreateSecondaryButton(window.ContentArea, LocalizationManager.Get("character.select.logout"));
            _logoutButton.onClick.AddListener(HandleLogout);

            // Error text
            _errorText = UIFactory.CreateText(
                window.ContentArea, "", UITheme.ErrorFontSize, UITheme.TextError,
                TextAnchor.MiddleCenter);
            var errorLayout = _errorText.gameObject.AddComponent<LayoutElement>();
            errorLayout.preferredHeight = 20f;
            _errorText.gameObject.SetActive(false);
        }

        protected override void OnShow()
        {
            ClearError();
            _selectedIndex = 0;
            HighlightCharacter(_selectedIndex);
        }

        private void Update()
        {
            if (!IsVisible) return;

            var keyboard = Keyboard.current;
            if (keyboard == null) return;

            // Arrow key navigation
            if (keyboard.upArrowKey.wasPressedThisFrame)
            {
                _selectedIndex = Mathf.Max(0, _selectedIndex - 1);
                HighlightCharacter(_selectedIndex);
            }
            else if (keyboard.downArrowKey.wasPressedThisFrame)
            {
                int maxIndex = _characters != null ? _characters.Length - 1 : -1;
                _selectedIndex = Mathf.Min(maxIndex, _selectedIndex + 1);
                HighlightCharacter(_selectedIndex);
            }

            // Enter selects character
            if (keyboard.enterKey.wasPressedThisFrame || keyboard.numpadEnterKey.wasPressedThisFrame)
            {
                if (_characters != null && _selectedIndex >= 0 && _selectedIndex < _characters.Length)
                {
                    HandleCharacterSelected(_characters[_selectedIndex].Id);
                }
            }

            // Escape logs out
            if (keyboard.escapeKey.wasPressedThisFrame)
            {
                HandleLogout();
            }
        }

        public void SetCharacterList(FlyAgain.Proto.CharacterInfo[] characters)
        {
            _characters = characters;
            RebuildCharacterCards();
        }

        private void RebuildCharacterCards()
        {
            // Clear existing character buttons
            foreach (var btn in _characterButtons)
            {
                if (btn != null)
                    Destroy(btn.gameObject);
            }
            _characterButtons.Clear();

            // Find container
            var window = RootTransform.Find("Window");
            if (window == null) return;

            var container = window.Find("Content/CharacterListContainer");
            if (container == null) return;

            // Create character cards
            if (_characters != null && _characters.Length > 0)
            {
                for (int i = 0; i < _characters.Length; i++)
                {
                    CreateCharacterCard(container, _characters[i], i);
                }
            }
            else
            {
                // Show empty state
                var emptyText = UIFactory.CreateText(
                    container,
                    LocalizationManager.Get("character.select.empty_slot"),
                    UITheme.LabelFontSize,
                    UITheme.TextSecondary,
                    TextAnchor.MiddleCenter);
                var emptyLayout = emptyText.gameObject.AddComponent<LayoutElement>();
                emptyLayout.preferredHeight = 60f;
            }

            // Show/hide Create button based on character count
            if (_createButton != null)
            {
                _createButton.gameObject.SetActive(_characters == null || _characters.Length < MaxCharacters);
            }

            _selectedIndex = 0;
        }

        private void CreateCharacterCard(Transform parent, FlyAgain.Proto.CharacterInfo characterInfo, int index)
        {
            // Create button container
            var cardGo = new GameObject($"CharacterCard_{index}");
            cardGo.transform.SetParent(parent, false);

            var cardImage = cardGo.AddComponent<Image>();
            cardImage.color = new Color(0.15f, 0.15f, 0.15f, 0.9f);

            var cardRect = cardGo.GetComponent<RectTransform>();
            var cardLayout = cardGo.AddComponent<LayoutElement>();
            cardLayout.preferredHeight = 70f;
            cardLayout.minHeight = 70f;

            // Add button component
            var button = cardGo.AddComponent<Button>();
            button.targetGraphic = cardImage;
            button.transition = Selectable.Transition.ColorTint;
            var colors = button.colors;
            colors.normalColor = new Color(0.15f, 0.15f, 0.15f, 1f);
            colors.highlightedColor = new Color(0.2f, 0.2f, 0.25f, 1f);
            colors.pressedColor = new Color(0.1f, 0.1f, 0.15f, 1f);
            colors.selectedColor = new Color(0.2f, 0.3f, 0.4f, 1f);
            button.colors = colors;

            long characterId = characterInfo.Id;
            button.onClick.AddListener(() => HandleCharacterSelected(characterId));

            // Add vertical layout for content
            var verticalLayout = UIFactory.AddVerticalLayout(cardGo, 12);
            verticalLayout.padding = new RectOffset(12, 12, 12, 12);

            // Character name
            var nameText = UIFactory.CreateText(
                cardRect,
                characterInfo.Name,
                UITheme.LabelFontSize + 2,
                UITheme.TextPrimary,
                TextAnchor.MiddleLeft);
            nameText.fontStyle = FontStyle.Bold;
            var nameLayout = nameText.gameObject.AddComponent<LayoutElement>();
            nameLayout.preferredHeight = 20f;

            // Character class and level
            string className = LocalizationManager.Get($"class.{characterInfo.CharacterClass.ToLower()}");
            string levelText = $"{className} | {LocalizationManager.Get("character.select.level")} {characterInfo.Level}";
            var classLevelText = UIFactory.CreateText(
                cardRect,
                levelText,
                UITheme.LabelFontSize,
                UITheme.TextSecondary,
                TextAnchor.MiddleLeft);
            var classLevelLayout = classLevelText.gameObject.AddComponent<LayoutElement>();
            classLevelLayout.preferredHeight = 18f;

            _characterButtons.Add(button);
        }

        private void HandleCharacterSelected(long characterId)
        {
            OnCharacterSelected?.Invoke(characterId);
        }

        private void HandleCreateCharacter()
        {
            OnCreateCharacterRequested?.Invoke();
        }

        private void HandleLogout()
        {
            OnLogoutRequested?.Invoke();
        }

        private void HighlightCharacter(int index)
        {
            // Visual highlight for selected character
            for (int i = 0; i < _characterButtons.Count; i++)
            {
                if (_characterButtons[i] != null)
                {
                    var image = _characterButtons[i].GetComponent<Image>();
                    if (image != null)
                    {
                        if (i == index)
                        {
                            // Highlighted
                            image.color = new Color(0.2f, 0.3f, 0.4f, 1f);
                        }
                        else
                        {
                            // Normal
                            image.color = new Color(0.15f, 0.15f, 0.15f, 1f);
                        }
                    }
                }
            }
        }

        public void ShowError(string message)
        {
            _errorText.text = message;
            _errorText.color = UITheme.TextError;
            _errorText.gameObject.SetActive(true);
        }

        public void ClearError()
        {
            _errorText.text = "";
            _errorText.gameObject.SetActive(false);
        }
    }
}
