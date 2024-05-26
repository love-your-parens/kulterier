// When plain htmx isn't quite enough, you can stick some custom JS here.
document.addEventListener(
    "DOMContentLoaded",
    function () {
        const backToTop = document.querySelector("#back-to-top")
        backToTop.addEventListener('click', function () {
            window.scroll({ top: 0, behavior: 'smooth' })
        })

        let scrollHookRunning = false;
        document.addEventListener("scroll", () => {
            if (!scrollHookRunning) {
                running = true;
                window.requestAnimationFrame(() => {
                    if (window.scrollY > window.innerHeight) {
                        backToTop.classList.remove("pointer-events-none")
                        backToTop.classList.remove("opacity-0")

                    } else {
                        backToTop.classList.add("pointer-events-none")
                        backToTop.classList.add("opacity-0")
                    }
                    running = false;
                })
            }
        })
    }
)

function showFilterPopup() {
    let popup = document.querySelector('#filter-popup')
    if (popup) {
        popup.addEventListener(
            "transitionend",
            () => popup.querySelector("#filter-popup--close").focus(),
            { once: true }
        )
        popup.classList.remove('invisible')
        popup.classList.replace('-top-[100vh]', 'top-0')
        popup.addEventListener("keyup", hideFilterPopupOnEsc)
    }
}

function hideFilterPopup() {
    let popup = document.querySelector('#filter-popup')
    if (popup) {
        popup.classList.replace('top-0', '-top-[100vh]')
        popup.classList.add('invisible')
    }
}

// Declared to yield a global reference.
// We can leverage this to add event listeners idempotently.
const hideFilterPopupOnEsc = function (e) {
    if (e.keyCode === 27) hideFilterPopup()
}

function showOverlayPopup() {
    let popup = document.querySelector('#overlay-popup')
    popup.classList.remove('invisible')
    popup.classList.replace('-left-[100vw]', 'left-0')
    popup.querySelector("#overlay-popup--close").focus()
    document.body.classList.add("overflow-hidden")
}


function hideOverlayPopup() {
    let popup = document.querySelector('#overlay-popup')
    popup.classList.replace('left-0', '-left-[100vw]')
    popup.classList.add('invisible')
    document.body.classList.remove("overflow-hidden")
}
