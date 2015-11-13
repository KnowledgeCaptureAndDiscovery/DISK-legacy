# DISK

## Installation

1. Clone the repository [https://github.com/IKCAP/DISK.git](https://github.com/IKCAP/DISK.git)

```bash
git clone https://github.com/IKCAP/DISK.git
```

2. Setup

    2.1 For Development

    ```bash
    python setup.py develop
    ```

    2.1 For Installation
    ```bash
    python setup.py install
    ```

## Configuring

Create a file .labkeycredentials.txt, with 4 entries
```bash
[default]
machine  = https://example.domain.com:8443/labkey
login    = <username>
password = <password>
project  = <project>
```

The tool loads configuration in following order

1. From .labkeycredentials.txt file in the user's home directory
2. .labkeycredentials.txt file in current directory.
3. File passed as through the command line's --config option.
4. Command line arguments for each option.
