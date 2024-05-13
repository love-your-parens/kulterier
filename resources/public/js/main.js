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
