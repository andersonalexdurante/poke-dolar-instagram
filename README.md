# Poke-Dolar-Instagram 👾💰

O **Poke-Dolar-Instagram** é um projeto desenvolvido para estudos pessoais, explorando tecnologias modernas para automatizar a coleta da cotação do dólar e associá-la a um Pokémon correspondente, publicando o resultado diariamente no Instagram. 📈📸

📝 Este projeto foi inspirado no PokeDolar Bot para Twitter/X: https://github.com/PokeDolar/pokedolar_bot

## 🛠️ Tecnologias Utilizadas

### ⚙️ Backend
- 🔥 **Quarkus** com **Java 23** e **GraalVM**, permitindo execução nativa e otimizada para cloud
- ☁️ **AWS Lambda**, utilizando:
    - 🏗️ Execução de uma imagem nativa gerada com GraalVM
    - 🐍 Um script em **Python** para geração dinâmica de imagens
    - 🔄 Um mecanismo de **refresh de token** para a API do Instagram
- 🐳 **Docker**, utilizado para empacotamento e execução de aplicações nativas

### ☁️ Infraestrutura e Cloud
- 📦 **AWS S3** para armazenamento de imagens geradas
- 📡 **AWS SNS e SSM** para gerenciamento de eventos e segurança
- ⏰ **AWS EventBridge** para agendamento de execuções automatizadas

### 🔗 Integrações e APIs
- 🎭 **PokeAPI**, utilizada para obter dados sobre os Pokémon
- 📲 **GraphAPI do Instagram**, utilizada para publicar as imagens de forma automatizada

## 🚀 Implantação e Funcionamento

O projeto é totalmente hospedado na AWS, onde os serviços são orquestrados para garantir a execução diária do processo. A aplicação principal roda como uma **AWS Lambda** compilada de forma nativa, garantindo alto desempenho. O fluxo de funcionamento ocorre da seguinte maneira:

1. ⏰ O **EventBridge** agenda a execução diária da Lambda principal.
2. 💰 A Lambda principal coleta a cotação do dólar e determina o Pokémon correspondente.
3. 🖼️ Um script em **Python**, executado por outra Lambda, gera uma imagem com as informações.
4. 📂 A imagem gerada é armazenada no **S3** e publicada automaticamente via **GraphAPI do Instagram**.
5. 🔄 O sistema mantém um mecanismo de **refresh de token**, garantindo a continuidade da autenticação na API do Instagram.

## 🤝 Contribuição

Esse projeto foi feito para aprendizado, mas se quiser contribuir, fique à vontade para abrir issues ou pull requests. 🚀

## 📜 Licença

Este projeto está sob a licença MIT. Consulte o arquivo `LICENSE` para mais informações.

---

**👨‍💻 Autor:** [Seu Nome](https://github.com/seu-usuario)
"""