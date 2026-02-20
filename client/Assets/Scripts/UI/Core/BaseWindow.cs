using UnityEngine;
using UnityEngine.UI;

namespace FlyAgain.UI.Core
{
    /// <summary>
    /// Reusable window panel with title bar and scrollable content area.
    /// Use BaseWindow.Create() to instantiate. The ContentArea transform can be
    /// populated with any child elements (input fields, buttons, etc.).
    /// </summary>
    public class BaseWindow : MonoBehaviour
    {
        public RectTransform ContentArea { get; private set; }

        private Text _titleText;

        /// <summary>
        /// Create a centered window with title bar and content area.
        /// </summary>
        public static BaseWindow Create(Transform parent, string title, float width, float height)
        {
            // Root panel
            var root = UIFactory.CreatePanel(parent, "Window", UITheme.WindowBackground);
            root.sizeDelta = new Vector2(width, height);
            root.anchorMin = new Vector2(0.5f, 0.5f);
            root.anchorMax = new Vector2(0.5f, 0.5f);
            root.anchoredPosition = Vector2.zero;

            var window = root.gameObject.AddComponent<BaseWindow>();

            // Root vertical layout: title bar on top, content fills remaining space
            var rootLayout = UIFactory.AddVerticalLayout(root.gameObject);
            rootLayout.childControlHeight = true;

            // Title bar
            var titleBar = UIFactory.CreatePanel(root, "TitleBar", UITheme.TitleBarBackground);
            var titleBarLayout = titleBar.gameObject.AddComponent<LayoutElement>();
            titleBarLayout.preferredHeight = UITheme.TitleBarHeight;

            window._titleText = UIFactory.CreateText(
                titleBar, title, UITheme.TitleFontSize, UITheme.TextPrimary,
                TextAnchor.MiddleCenter);
            UIFactory.StretchToParent(window._titleText.rectTransform);

            // Content area with padding and vertical layout for child elements
            var contentGo = new GameObject("Content", typeof(RectTransform));
            contentGo.transform.SetParent(root, false);

            var contentLayout = contentGo.AddComponent<LayoutElement>();
            contentLayout.flexibleHeight = 1f;

            window.ContentArea = contentGo.GetComponent<RectTransform>();

            int pad = (int)UITheme.WindowPadding;
            UIFactory.AddVerticalLayout(
                contentGo, UITheme.ElementSpacing,
                new RectOffset(pad, pad, pad, pad));

            return window;
        }

        public void SetTitle(string title) => _titleText.text = title;
    }
}
