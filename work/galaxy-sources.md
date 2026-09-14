# Galactic-coordinate source

The renderer uses the fixed Galactic-to-ICRS rotation given by the transpose of
equation 4.62 in the official Gaia EDR3 documentation:

- ESA/Gaia, “Transformations of astrometric data and error propagation,” section
  4.1.7: https://gea.esac.esa.int/archive/documentation/GEDR3/Data_processing/chap_cu3ast/sec_cu3ast_intro/ssec_cu3ast_intro_tansforms.html

That source gives the ICRS coordinates of the north Galactic pole as
(192.85948°, +27.12825°), the ascending-node longitude as 32.93192°, and the
orthogonal ICRS-to-Galactic matrix. `GalacticCoordinates` transposes that matrix
for Galactic-to-ICRS conversion. The visual brightness, width modulation, and
dust lanes are original procedural artwork; they are illustrative rather than a
photometric survey map and contain no copied imagery.
