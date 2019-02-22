#!/bin/bash
# ---------------------------------------------------------------------------- #

set -e

pdflatex -shell-escape main.tex
pdflatex -shell-escape main.tex
pdflatex -shell-escape main.tex

xdg-open main.pdf || open main.pdf

# ---------------------------------------------------------------------------- #
