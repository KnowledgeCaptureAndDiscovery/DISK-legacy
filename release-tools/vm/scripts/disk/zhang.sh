#!/bin/bash

set -e


# -------------
# Zhang et. al.
# -------------


yum -y install gcc ncurses-devel
pip install --upgrade Pillow

# ---------
# SAM tools
# ---------

SAMTOOLS_VERSION='1.3.1'

curl --location \
     --output samtools-${SAMTOOLS_VERSION}.tar.bz2 \
     "https://github.com/samtools/samtools/releases/download/${SAMTOOLS_VERSION}/samtools-${SAMTOOLS_VERSION}.tar.bz2"
tar --bzip2 --extract --verbose --file samtools-${SAMTOOLS_VERSION}.tar.bz2

cd samtools-${SAMTOOLS_VERSION}

./configure
make
make install

cd ..
rm --recursive --force samtools-${SAMTOOLS_VERSION}*


#----------
# BCF Tools
# ---------

BCFTOOLS_VERSION='1.3.1'

curl --location \
     --output bcftools-${BCFTOOLS_VERSION}.tar.bz2 \
     "https://github.com/samtools/bcftools/releases/download/${BCFTOOLS_VERSION}/bcftools-${BCFTOOLS_VERSION}.tar.bz2"
tar --bzip2 --extract --verbose --file bcftools-${BCFTOOLS_VERSION}.tar.bz2

cd bcftools-${BCFTOOLS_VERSION}

make
make install

cd ..
rm --recursive --force bcftools-${BCFTOOLS_VERSION}*


# -------
# Bowtie2
# -------

BOWTIE2_VERSION='2.2.9'

curl --location \
     --output bowtie2-${BOWTIE2_VERSION}-linux-x86_64.zip \
     "http://downloads.sourceforge.net/project/bowtie-bio/bowtie2/${BOWTIE2_VERSION}/bowtie2-${BOWTIE2_VERSION}-linux-x86_64.zip?r=https://sourceforge.net/projects/bowtie-bio/files/bowtie2/${BOWTIE2_VERSION}/&ts=`date +\"%s\"`&use_mirror=heanet"
unzip bowtie2-${BOWTIE2_VERSION}-linux-x86_64.zip

cd bowtie2-${BOWTIE2_VERSION}
cp bowtie2* /usr/local/bin/

cd ..
rm --recursive --force bowtie2-${BOWTIE2_VERSION}*


# -------
# Tophat2
# -------

TOPHAT2_VERSION='2.1.1'

curl --location \
     --output tophat-${TOPHAT2_VERSION}.Linux_x86_64.tar.gz \
     "https://ccb.jhu.edu/software/tophat/downloads/tophat-${TOPHAT2_VERSION}.Linux_x86_64.tar.gz"
tar --gzip --extract --verbose --file tophat-${TOPHAT2_VERSION}.Linux_x86_64.tar.gz

cd tophat-${TOPHAT2_VERSION}.Linux_x86_64

rm --force AUTHORS LICENSE README
cp --recursive --force * /usr/local/bin

cd ..
rm --recursive --force tophat-${TOPHAT2_VERSION}.Linux_x86_64*


# ---------
# Cufflinks
# ---------

CUFFLINKS_VERSION='2.2.1'

curl --location \
     --output cufflinks-${CUFFLINKS_VERSION}.Linux_x86_64.tar.gz \
     "http://cole-trapnell-lab.github.io/cufflinks/assets/downloads/cufflinks-${CUFFLINKS_VERSION}.Linux_x86_64.tar.gz"
tar --gzip --extract --verbose --file cufflinks-${CUFFLINKS_VERSION}.Linux_x86_64.tar.gz

cd cufflinks-${CUFFLINKS_VERSION}.Linux_x86_64

rm --force AUTHORS LICENSE README
cp --force * /usr/local/bin

cd ..
rm --recursive --force cufflinks-${CUFFLINKS_VERSION}.Linux_x86_64*


# -------------
# Cell Profiler
# -------------

JAVA_VERSION=`java -version 2>&1 | head -1 | sed -e 's/.*"\(.*\)_.*"/\1/g'`

yum -y install numpy scipy python-zmq java-${JAVA_VERSION}-openjdk-devel Cython MySQL-python gcc-c++
pip install matplotlib
pip install cellprofiler
yum -y remove gcc-c++ Cython


# -----
# RSeQC
# -----

pip install RSeQC


# ----------
# R Packages
# ----------

yum -y install R-devel openssl-devel libcurl-devel

Rscript - << EOT
install.packages("codetools", repos='http://cran.us.r-project.org')
install.packages("devtools", repos='http://cran.us.r-project.org')
install.packages("curl", repos='http://cran.us.r-project.org')
library(devtools)
install_github("kassambara/easyGgplot2")
install.packages("ggplot2", repos='http://cran.us.r-project.org')
install.packages("reshape", repos='http://cran.us.r-project.org')
install.packages("reshape2", repos='http://cran.us.r-project.org')
install.packages("dplyr", repos="http://R-Forge.R-project.org")
install.packages("Vennerable", repos="http://R-Forge.R-project.org")
install.packages("PerformanceAnalytics", repos="http://R-Forge.R-project.org")
install.packages("psych", repos='http://cran.us.r-project.org')
install.packages("circlize", repos='http://cran.us.r-project.org')
install.packages("corrplot", repos='http://cran.us.r-project.org')
install.packages("Hmisc", repos='http://cran.us.r-project.org')
install.packages("gdata", repos='http://cran.us.r-project.org')
source('http://bioconductor.org/biocLite.R')
biocLite()
biocLite("customProDB")
biocLite("Rsubread")
biocLite("limma")
biocLite("ComplexHeatmap")
biocLite("ConsensusClusterPlus")
EOT


# --------------
# WINGS - Labkey
# --------------

git clone https://github.com/IKCAP/DISK.git

cd DISK

python setup.py install

cd ..
rm --recursive --force DISK
