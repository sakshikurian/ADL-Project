package com.ecotracker.ui.chatbot

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ecotracker.R
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.FloatingActionButton
import okhttp3.*
import java.io.IOException
import java.util.Locale

class ChatbotActivity : AppCompatActivity() {

    private lateinit var rvChatMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: FloatingActionButton
    private lateinit var btnBack: ImageButton
    private lateinit var btnClearChat: ImageButton

    private lateinit var chipRecycling: Chip
    private lateinit var chipPlastics: Chip
    private lateinit var chipCompost: Chip
    private lateinit var chipEnergy: Chip
    private lateinit var chipFootprint: Chip

    private val chatAdapter = ChatAdapter()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val httpClient = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chatbot)

        initViews()
        setupListeners()

        // Initial welcome message from EcoBot
        chatAdapter.addMessage(
            ChatMessage(
                text = getString(R.string.chatbot_welcome),
                isUser = false
            )
        )
    }

    private fun initViews() {
        rvChatMessages = findViewById(R.id.rvChatMessages)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        btnBack = findViewById(R.id.btnBack)
        btnClearChat = findViewById(R.id.btnClearChat)

        chipRecycling = findViewById(R.id.chipRecycling)
        chipPlastics = findViewById(R.id.chipPlastics)
        chipCompost = findViewById(R.id.chipCompost)
        chipEnergy = findViewById(R.id.chipEnergy)
        chipFootprint = findViewById(R.id.chipFootprint)

        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        rvChatMessages.layoutManager = layoutManager
        rvChatMessages.adapter = chatAdapter
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }

        btnClearChat.setOnClickListener {
            chatAdapter.clearMessages()
            chatAdapter.addMessage(
                ChatMessage(
                    text = getString(R.string.chatbot_welcome),
                    isUser = false
                )
            )
        }

        btnSend.setOnClickListener { sendMessage() }

        etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }

        chipRecycling.setOnClickListener {
            askQuestion("What are the general rules for household recycling?")
        }
        chipPlastics.setOnClickListener {
            askQuestion("Can you explain plastic recycling numbers 1 through 7?")
        }
        chipCompost.setOnClickListener {
            askQuestion("How do I start composting at home or in college?")
        }
        chipEnergy.setOnClickListener {
            askQuestion("What are practical ways to save electricity and reduce energy waste?")
        }
        chipFootprint.setOnClickListener {
            askQuestion("How do I calculate and reduce my carbon footprint?")
        }
    }

    private fun askQuestion(query: String) {
        etMessage.setText(query)
        sendMessage()
    }

    private fun sendMessage() {
        val query = etMessage.text.toString().trim()
        if (query.isEmpty()) return

        // 1. Add user message
        chatAdapter.addMessage(ChatMessage(text = query, isUser = true))
        etMessage.text.clear()
        rvChatMessages.smoothScrollToPosition(chatAdapter.itemCount - 1)

        // 2. Provide answer (Offline Knowledge Engine + optional API fallback)
        processBotResponse(query)
    }

    private fun processBotResponse(query: String) {
        // Subtle simulated typing delay for natural conversation feel
        mainHandler.postDelayed({
            val botAnswer = generateEcoAnswer(query)
            chatAdapter.addMessage(ChatMessage(text = botAnswer, isUser = false))
            rvChatMessages.smoothScrollToPosition(chatAdapter.itemCount - 1)
        }, 500)
    }

    /**
     * Comprehensive Semantic Knowledge Base for Sustainability, Recycling,
     * Plastics Classification, Composting, Carbon Tracking, and Eco-friendly Living.
     */
    private fun generateEcoAnswer(input: String): String {
        val q = input.lowercase(Locale.ROOT)

        return when {
            // Plastic identification and recycling
            q.contains("plastic") || q.contains("#") || q.contains("number") -> {
                when {
                    q.contains("1") || q.contains("pet") || q.contains("pete") ->
                        "🏷️ **Plastic #1 (PET / PETE)**: Polyethylene Terephthalate.\n" +
                                "• **Common items**: Water/soda bottles, peanut butter jars.\n" +
                                "• **Recyclability**: Widely recycled in almost all municipal programs!\n" +
                                "• **Tip**: Empty contents, give it a quick rinse, and leave the cap screwed on."

                    q.contains("2") || q.contains("hdpe") ->
                        "🏷️ **Plastic #2 (HDPE)**: High-Density Polyethylene.\n" +
                                "• **Common items**: Milk jugs, shampoo bottles, detergent containers.\n" +
                                "• **Recyclability**: Highly recyclable and in high demand by recyclers!\n" +
                                "• **Tip**: Stiff and durable; make sure it's rinsed clean."

                    q.contains("3") || q.contains("pvc") ->
                        "🏷️ **Plastic #3 (PVC)**: Polyvinyl Chloride.\n" +
                                "• **Common items**: Plumbing pipes, shower curtains, shrink wrap.\n" +
                                "• **Recyclability**: Rarely accepted in curbside recycling due to chlorine additives. Check special hardware store drop-offs."

                    q.contains("4") || q.contains("ldpe") ->
                        "🏷️ **Plastic #4 (LDPE)**: Low-Density Polyethylene.\n" +
                                "• **Common items**: Grocery plastic bags, bread bags, squeeze bottles.\n" +
                                "• **Recyclability**: Do NOT place plastic bags in curbside bins (they tangle sorting machinery). Bring them to grocery store plastic film drop-off bins!"

                    q.contains("5") || q.contains("pp") ->
                        "🏷️ **Plastic #5 (PP)**: Polypropylene.\n" +
                                "• **Common items**: Yogurt cups, medicine bottles, takeout tubs, syrup containers.\n" +
                                "• **Recyclability**: Accepted in most modern recycling centers. Rinse clean before tossing in your bin."

                    q.contains("6") || q.contains("ps") || q.contains("styrofoam") ->
                        "🏷️ **Plastic #6 (PS)**: Polystyrene / Styrofoam.\n" +
                                "• **Common items**: Disposable coffee cups, egg cartons, packing peanuts.\n" +
                                "• **Recyclability**: Very difficult to recycle economically. Avoid single-use styrofoam whenever possible; prefer reusable thermoses!"

                    q.contains("7") || q.contains("other") || q.contains("bpa") ->
                        "🏷️ **Plastic #7 (OTHER)**: Mixed resins, polycarbonates, or bioplastics (PLA).\n" +
                                "• **Common items**: Baby bottles, sunglasses, 3-gallon water coolers, compostable cups.\n" +
                                "• **Recyclability**: Generally not curbside recyclable. Note: PLA bioplastics belong in commercial compost, not plastic recycling!"

                    else ->
                        "♻️ **Plastics Quick Reference (#1 to #7)**:\n" +
                                "• **#1 (PET)** & **#2 (HDPE)**: Always curbside recyclable (bottles, jugs).\n" +
                                "• **#3 (PVC)**: Seldom recyclable.\n" +
                                "• **#4 (LDPE)**: Grocery bags (drop-off at supermarket collection).\n" +
                                "• **#5 (PP)**: Yogurt cups & tubs (mostly recyclable).\n" +
                                "• **#6 (PS)**: Styrofoam (avoid & refuse).\n" +
                                "• **#7 (Other)**: Special drop-off or commercial compost."
                }
            }

            // Composting
            q.contains("compost") || q.contains("food scrap") || q.contains("organic") ->
                "🌱 **Composting Guide**:\n" +
                        "• **Greens (Nitrogen)**: Vegetable peels, fruit rinds, coffee grounds, tea leaves, fresh grass clippings.\n" +
                        "• **Browns (Carbon)**: Dry leaves, cardboard scraps, egg cartons, sawdust, newspaper.\n" +
                        "• **Golden Ratio**: Aim for 2 to 3 parts Browns to 1 part Greens.\n" +
                        "• **DO NOT Compost**: Meat, fish, dairy, grease, cooked bones, or diseased plant leaves."

            // Pizza box / specific item queries
            q.contains("pizza") ->
                "🍕 **Pizza Boxes**:\n" +
                        "• If greasy or cheesy: **Do NOT** put the greasy bottom into paper recycling. Oil ruins paper fiber recycling pulp!\n" +
                        "• **Best Practice**: Tear off the clean top lid to recycle with cardboard, and put the greasy bottom into compost or trash."

            // Batteries, Electronics, E-waste
            q.contains("battery") || q.contains("batteries") || q.contains("electronic") || q.contains("phone") || q.contains("e-waste") ->
                "🔋 **Battery & E-Waste Safety**:\n" +
                        "• Never throw alkaline, lithium-ion, or rechargeable batteries in standard bins (fire hazard at waste facilities!).\n" +
                        "• Drop off used electronics and batteries at campus collection kiosks, Best Buy, or municipal hazardous waste depots."

            // Energy & Electricity saving
            q.contains("energy") || q.contains("electric") || q.contains("power") || q.contains("light") || q.contains("bulb") ->
                "⚡ **Top Energy Conservation Actions**:\n" +
                        "1. **Eliminate Phantom Loads**: Use smart power strips to cut standby power on TVs and chargers.\n" +
                        "2. **LED Lighting**: LEDs use up to 80% less energy than incandescent bulbs and last 15x longer.\n" +
                        "3. **Cold Water Laundry**: ~90% of a washing machine's energy goes toward heating water.\n" +
                        "4. **Thermostat Rule**: Lowering 1°C in winter or raising 1°C in summer saves ~6% on HVAC energy."

            // Carbon footprint & transportation
            q.contains("carbon") || q.contains("footprint") || q.contains("co2") || q.contains("emission") ->
                "👣 **Carbon Footprint Essentials**:\n" +
                        "• **Walking/Biking**: 0 g CO₂/km (EcoTracker walking saves ~0.17 kg CO₂/km compared to driving!).\n" +
                        "• **Public Transit**: Cuts personal commuter emissions by 60–75%.\n" +
                        "• **Diet**: Incorporating 'Meatless Mondays' can reduce your yearly carbon impact by ~300 kg CO₂.\n" +
                        "• **Energy**: Choose renewable provider tariffs or air-dry laundry."

            // Water saving
            q.contains("water") || q.contains("shower") || q.contains("leak") || q.contains("tap") ->
                "💧 **Water Conservation Tips**:\n" +
                        "• A standard 10-minute shower uses ~90 Liters. Cutting it to 5 minutes saves ~45 L every day!\n" +
                        "• Turn off the faucet while brushing teeth to save ~12 Liters per session.\n" +
                        "• Fix dripping faucets: one drop per second wastes over 3,000 Liters annually."

            // Campus or EcoTracker specific
            q.contains("campus") || q.contains("ecotracker") || q.contains("app") || q.contains("navigation") || q.contains("point") ->
                "🌿 **EcoTracker App Features**:\n" +
                        "• **Walking Navigation**: Track campus walking routes and calculate instant CO₂ offsets with OpenStreetMap.\n" +
                        "• **Eco Lens**: Verify actions with GPS geotagged camera photos & voice notes.\n" +
                        "• **ML Eco-Impact**: Run machine-learning models to forecast your weekly environmental impact.\n" +
                        "• **Smart Alerts**: Keep your daily streak alive and achieve eco-guardian milestones!"

            // General greetings / help
            q.contains("hello") || q.contains("hi") || q.contains("hey") ->
                "Hello! 🌿 I am EcoBot. What sustainability or recycling question can I help you solve today?"

            q.contains("thank") || q.contains("thanks") ->
                "You're very welcome! Keep up the fantastic eco habits. Every small action creates a cleaner planet! 🌍"

            // Default intelligent fallback
            else ->
                "🌿 Great sustainability question! Here are key guidelines:\n" +
                        "• **Recycling**: Ensure items are empty, clean, and dry before binning.\n" +
                        "• **Plastics**: Check the resin code (#1 to #7) stamped on the bottom.\n" +
                        "• **Reduction**: Reusable bags, water bottles, and walking routes have the highest positive impact.\n\n" +
                        "Ask me specifically about **plastics #1-#7**, **composting**, **energy tips**, or **reducing CO₂** for deeper details!"
        }
    }
}
