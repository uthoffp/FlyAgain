using UnityEngine;

namespace FlyAgain.UI.Core
{
    /// <summary>
    /// Abstract base class for all UI screens. Provides lifecycle management
    /// (Initialize, Show, Hide) and a container RectTransform that fills the canvas.
    /// Subclasses implement OnBuild() to create their UI content.
    /// </summary>
    public abstract class BaseScreen : MonoBehaviour
    {
        public bool IsVisible => gameObject.activeSelf;

        protected RectTransform RootTransform { get; private set; }

        public void Initialize()
        {
            RootTransform = GetComponent<RectTransform>();
            UIFactory.StretchToParent(RootTransform);
            OnBuild();
            Hide();
        }

        /// <summary>
        /// Build the screen's UI hierarchy. Called once during initialization.
        /// </summary>
        protected abstract void OnBuild();

        public virtual void Show()
        {
            gameObject.SetActive(true);
            OnShow();
        }

        public virtual void Hide()
        {
            gameObject.SetActive(false);
            OnHide();
        }

        protected virtual void OnShow() { }
        protected virtual void OnHide() { }
    }
}
