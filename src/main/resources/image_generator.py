from PIL import Image, ImageDraw, ImageFont, ImageFilter
import sys
from collections import Counter
import math
import json
import base64
import io
from PIL import Image
import logging
import os

# Configurar logging
logger = logging.getLogger()
logger.setLevel(logging.INFO)

def lambda_handler(event, context):
    try:
        logger.info("Lambda execution started.")

        # Extract input data from the Lambda event
        input_image_base64 = event.get("image")  # Base64-encoded image
        dollar_rate = event.get("dollar_rate")
        pokedex_number = event.get("pokedex_number")
        pokemon_name = event.get("pokemon_name")
        old_pokedex_number = event.get("old_pokedex_number", None)

        logger.info(f"Received event: {json.dumps(event)}")

        if not input_image_base64 or not dollar_rate or not pokedex_number or not pokemon_name:
            logger.error("Missing required parameters.")
            return {
                "statusCode": 400,
                "body": json.dumps({"error": "Missing required parameters."})
            }

        # Decode base64 image
        try:
            input_image_bytes = base64.b64decode(input_image_base64)
            logger.info("Image successfully decoded from Base64.")
        except Exception as decode_error:
            logger.error(f"Error decoding base64 image: {str(decode_error)}")
            return {
                "statusCode": 400,
                "body": json.dumps({"error": "Invalid Base64 image."})
            }

        # Call the original function to generate the image
        output_bytes = create_image(input_image_bytes, dollar_rate, pokedex_number, pokemon_name, old_pokedex_number)

        if output_bytes is None or len(output_bytes) == 0:
            logger.error("Erro: Nenhum byte retornado pela função create_image!")
            return None

        # Encode the output image to base64
        output_base64 = base64.b64encode(output_bytes).decode("utf-8")
        logger.info(f"Imagem gerada e codificada com sucesso: {output_base64[:50]}...")

        return {
            "statusCode": 200,
            "headers": {"Content-Type": "application/json"},
            "body": json.dumps({"image": output_base64})
        }

    except Exception as e:
        logger.error(f"Unexpected error: {str(e)}", exc_info=True)
        return {
            "statusCode": 500,
            "body": json.dumps({"error": str(e)})
        }


def get_dominant_colors(image, num_colors=5, final_num_colors=2):
    # Resize image for faster processing
    image = image.resize((100, 100))
    pixels = list(image.getdata())

    # Remove transparent pixels (alpha < 128)
    opaque_pixels = [pixel for pixel in pixels if len(pixel) == 4 and pixel[3] > 128]

    # Fallback if no opaque pixels found
    if not opaque_pixels:
        return [(255, 69, 0), (30, 144, 255)]  # Orange/Blue fallback

    # Convert to RGB and get color frequencies
    rgb_pixels = [pixel[:3] for pixel in opaque_pixels]
    color_counts = Counter(rgb_pixels).most_common(num_colors)

    # Extract colors ensuring at least `final_num_colors` distinct colors
    colors = [color[0] for color in color_counts]

    # Darken colors if they are too white (all channels > 200)
    for i in range(len(colors)):
        if all(c > 200 for c in colors[i]):  # Check if color is too white
            colors[i] = tuple(max(0, c - 60) for c in colors[i])  # Darken it

    # If fewer colors than needed, create darker/lighter variants
    while len(colors) < final_num_colors:
        base_color = colors[0]  # Use the first color as base
        colors.append(tuple(max(0, c - 80) for c in base_color))  # Darker version

    # Function to calculate Euclidean distance between two colors
    def color_distance(color1, color2):
        return math.sqrt(sum((c1 - c2) ** 2 for c1, c2 in zip(color1, color2)))

    # Find the two most different colors
    max_distance = -1
    selected_colors = None

    # Compare all pairs of colors
    for i in range(len(colors)):
        for j in range(i + 1, len(colors)):
            distance = color_distance(colors[i], colors[j])
            if distance > max_distance:
                max_distance = distance
                selected_colors = (colors[i], colors[j])

    return selected_colors

def create_gradient_background(draw, width, height, color1, color2):
    # Determine which color is darker
    def luminance(color):
        # Calculate luminance using the formula for perceived brightness
        return (0.299 * color[0] + 0.587 * color[1] + 0.114 * color[2])

    # Compare luminance to determine darker and lighter colors
    if luminance(color1) > luminance(color2):
        lighter_color, darker_color = color1, color2
    else:
        lighter_color, darker_color = color2, color1

    # Create vertical gradient (darker at the top, lighter at the bottom)
    for i in range(height):
        ratio = i / height
        r = int(darker_color[0] * (1 - ratio) + lighter_color[0] * ratio)
        g = int(darker_color[1] * (1 - ratio) + lighter_color[1] * ratio)
        b = int(darker_color[2] * (1 - ratio) + lighter_color[2] * ratio)
        draw.line([(0, i), (width, i)], fill=(r, g, b))

def create_image(input_image_bytes, dollar_rate, pokedex_number, pokemon_name, old_pokedex_number):
    WIDTH, HEIGHT = 1080, 1080
    logger.info("Iniciando criação da imagem.")

    try:
        # Load Pokémon image com canal alpha
        pokemon_img = Image.open(io.BytesIO(input_image_bytes)).convert("RGBA")
        logger.info("Imagem do Pokémon carregada com sucesso.")

        # Obter cores dominantes
        colors = get_dominant_colors(pokemon_img)
        logger.info(f"Cores dominantes extraídas: {colors}")

        # Criar background
        img = Image.new('RGB', (WIDTH, HEIGHT))
        draw = ImageDraw.Draw(img)
        create_gradient_background(draw, WIDTH, HEIGHT, colors[0], colors[1] if len(colors) > 1 else colors[0])
        logger.info("Background gradiente criado.")

        # Definir fontes
        font_path = "/opt/python/fonts/arialbd.ttf"
        font_title = ImageFont.truetype(font_path, 90)
        font_price = ImageFont.truetype(font_path, 150)
        font_pokemon = ImageFont.truetype(font_path, 60)
        logger.info("Fontes carregadas.")

        # Determinar variação do dólar
        dollar_up = int(pokedex_number) > int(old_pokedex_number) if old_pokedex_number else False
        price_text = f"R$ {str(dollar_rate)[:str(dollar_rate).index('.')+3]}".replace('.', ',')
        valuation_text = "O DÓLAR ESTÁ EM " if not old_pokedex_number else ("O DÓLAR SUBIU " if dollar_up else "O DÓLAR CAIU ")
        pokedex_text = f"#{pokedex_number} - {pokemon_name}"

        logger.info(f"Texto do valor do dólar: {price_text}")
        logger.info(f"Texto de variação do dólar: {valuation_text}")
        logger.info(f"Texto da Pokédex: {pokedex_text}")

        # Definir cores
        price_color = "red" if dollar_up else "#00FF7F"
        text_color = "white"
        shadow_color = "black"

        # Função para desenhar texto com sombra
        def draw_text_with_shadow(text, font, y, color, shadow_offset=4):
            bbox = draw.textbbox((0, 0), text, font=font)
            text_width, text_height = bbox[2] - bbox[0], bbox[3] - bbox[1]
            x = (WIDTH - text_width) // 2

            draw.text((x + shadow_offset, y + shadow_offset), text, font=font, fill=shadow_color)
            draw.text((x, y), text, font=font, fill=color)

        # Adicionar textos na imagem
        draw_text_with_shadow(valuation_text, font_title, 50, text_color)
        draw_text_with_shadow(price_text, font_price, 170, price_color, 6)
        logger.info("Textos adicionados na imagem.")

        # Redimensionar e posicionar a imagem do Pokémon
        pokemon_img = pokemon_img.resize((500, 500))
        shadow = pokemon_img.copy().convert("L").point(lambda p: p > 0 and 100)
        shadow = Image.merge("RGBA", (shadow, shadow, shadow, shadow))
        shadow = shadow.filter(ImageFilter.GaussianBlur(10))

        img.paste(shadow, (290 + 10, 350 + 10), shadow)
        img.paste(pokemon_img, (290, 350), pokemon_img)
        logger.info("Imagem do Pokémon adicionada com sombra.")

        # Adicionar nome e número do Pokémon
        draw_text_with_shadow(pokedex_text, font_pokemon, 950, text_color)
        logger.info("Adicionado nome e numero do pokemon.")

        try:
            logger.info("Convertendo imagem para bytes")
            output_bytes = io.BytesIO()
            img.save(output_bytes, format="PNG")
            logger.info(f"Imagem criada com sucesso. Tamanho final: {output_bytes.tell()} bytes.")
            return output_bytes.getvalue()
        except Exception as e:
            logger.error(f"Erro ao converter imagem para bytes: {e}", exc_info=True)
            return None
    except Exception as e:
        logger.error(f"Erro ao criar a imagem: {e}", exc_info=True)
        return None
